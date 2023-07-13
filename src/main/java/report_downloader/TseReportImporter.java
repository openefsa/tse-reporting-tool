package report_downloader;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import amend_manager.ReportImporter;
import dataset.Dataset;
import formula.FormulaException;
import providers.ITableDaoService;
import providers.TseReportService;
import table_relations.Relation;
import table_skeleton.TableCell;
import table_skeleton.TableRow;
import tse_config.CustomStrings;
import tse_report.TseReport;
import tse_summarized_information.SummarizedInfo;
import xlsx_reader.TableSchemaList;

/**
 * Download and import a dataset, managing also all the amendments
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class TseReportImporter extends ReportImporter {

	private static final Logger LOGGER = LogManager.getLogger(TseReportImporter.class);

	private TseReportService reportService;
	private ITableDaoService daoService;

	// temporary variables
	private TseReport mainReport;
	private Collection<SummarizedInfo> summInfos;
	private HashMap<String, TableRow> cases; // caseId, case

	/**
	 * Download and import a dataset, managing also all the amendments
	 */
	public TseReportImporter(TseReportService reportService, ITableDaoService daoService) {
		super(CustomStrings.RES_ID_COL, CustomStrings.SENDER_DATASET_ID_COL, reportService, daoService);
		this.reportService = reportService;
		this.daoService = daoService;
		summInfos = new ArrayList<>();
		cases = new HashMap<>();
	}

	/**
	 * Check if a row is a summarized information row or not if not the row is an
	 * analytical result
	 * 
	 * @param row
	 * @return
	 */
	private static boolean isSummarizedInfo(TableRow row) {
		return row.getCode(CustomStrings.PARAM_TYPE_COL).equals(CustomStrings.SUMMARIZED_INFO_PARAM_TYPE);
	}

	/**
	 * Import all the summarized information into the db
	 * 
	 * @param report1
	 * @param datasetRows
	 * @throws FormulaException
	 * @throws ParseException
	 */
	private void importSummarizedInformation(TseReport report1, Collection<TableRow> datasetRows) throws FormulaException, ParseException {
		// first process the summarized information
		for (TableRow row : datasetRows) {

			// if we have a summarized information
			// import it
			if (isSummarizedInfo(row)) {
				SummarizedInfo summInfo = new SummarizedInfo();

				// if random genotyping, create the summarized information
				if (TseReportService.isRGTResult(row)) {
					summInfo = extractSummarizedInfo(report1, row, true);

					// create the summarized information
					daoService.add(summInfo);

					// add in the cache in order to avoid to save the same summInfo
					// for the different results
					summInfos.add(summInfo);

					LOGGER.info("Created fake RGT summarized information");
				} else {
					SummarizedInfo si = extractSummarizedInfo(report1, row, false);

					// save it in the database
					daoService.add(si);
					LOGGER.info("Imported summ info; sampId=" + reportService.getSampId(si));

					// save it in the cache
					summInfos.add(si);
				}
				LOGGER.info(summInfos);
			}
		}
	}

	/**
	 * Import all the cases and analytical results
	 * 
	 * @param report1
	 * @param datasetRows
	 * @throws FormulaException
	 * @throws ParseException
	 */
	private void importCasesAndResults(TseReport report1, Collection<TableRow> datasetRows) throws FormulaException, ParseException {
		// process the cases and analytical results
		for (TableRow row : datasetRows) {
			if (!isSummarizedInfo(row)) {
				SummarizedInfo summInfo = new SummarizedInfo();

				// if random genotyping, create the summarized information
				if (!TseReportService.isRGTResult(row)) {
					row.put(CustomStrings.REPORT_ID_COL, report1.getDatabaseId()); // Report is needed for results
																					// formulas (sampId)

					String origSampId = TseReportService.getOrigSampIdFrom(row);

					// get the summarized info related to the case/result
					summInfo = getSummInfoByOrigSampId(origSampId);

					LOGGER.info("Related summarized info with same origSampId= " + summInfo);
					// progId, sampEventId
				}

				if (summInfo == null) {
					String origSampId = TseReportService.getOrigSampIdFrom(row);
					String hashes = "";
					for (SummarizedInfo si : summInfos) {
						hashes += TseReportService.getOrigSampIdFrom(si) + "\n";
					}
					
					if (origSampId == null) {
						LOGGER.error("Can not find origSampId: " + origSampId + "for row: " + row);

						throw new ParseException(
								"No aggregated data was found related to sampOrigId=" + origSampId + " for individual case="
										+ row + ". Available aggregated data are: " + summInfos + "with hashes" + hashes,
								0);	
					}
				}

				// import the case
				TableRow caseInfo = importCase(report1, summInfo, row);

				// import the result
				TableRow result = importResult(report1, summInfo, caseInfo, row);
				LOGGER.info("Imported analytical result with database id=" + result.getDatabaseId());
			}
		}
	}

	/**
	 * Import the case if possible
	 * 
	 * @param report1
	 * @param row
	 * @return
	 * @throws FormulaException
	 * @throws ParseException
	 */
	private TableRow importCase(TseReport report1, SummarizedInfo summInfo, TableRow row) throws FormulaException, ParseException {
		// extract the case from the row
		TableRow currentCaseInfo = extractCase(report1, summInfo, row);

		// import the case info if not already imported
		if (currentCaseInfo.getDatabaseId() == -1) {
			// import case in the db
			daoService.add(currentCaseInfo);

			String sampId = currentCaseInfo.getLabel(CustomStrings.SAMPLE_ID_COL);
			LOGGER.info("Imported case/sample with database id=" + currentCaseInfo.getDatabaseId() + ", sampId=" + sampId);

			if (sampId == null) {
				LOGGER.error("No sample id was found for " + currentCaseInfo);
				return currentCaseInfo;
			}

			// save the case in the cache by its sample id
			cases.put(sampId, currentCaseInfo);
		}

		return currentCaseInfo;
	}

	/**
	 * Import the result into the db
	 * 
	 * @param report1
	 * @param summInfo
	 * @param caseInfo
	 * @param row
	 * @return
	 * @throws ParseException
	 */
	private TableRow importResult(TseReport report1, SummarizedInfo summInfo, TableRow caseInfo, TableRow row) throws ParseException {
		// then import the analytical result
		TableRow result = extractAnalyticalResult(report1, summInfo, caseInfo, row);
		LOGGER.info("Analytical Results to be imported: ", result);
		
		// save the result into the db
		daoService.add(result);

		return result;
	}

	/**
	 * Extract the summarized information data from the current row
	 * 
	 * @param report1
	 * @param row
	 * @return
	 * @throws FormulaException
	 * @throws ParseException
	 */
	private SummarizedInfo extractSummarizedInfo(TseReport report1, TableRow row, boolean isRGT) throws ParseException {
		// set the summarized information schema
		row.setSchema(TableSchemaList.getByName(CustomStrings.SUMMARIZED_INFO_SHEET));

		HashMap<String, TableCell> rowValues = new HashMap<>();
		TSEFormulaDecomposer decomposer = new TSEFormulaDecomposer();
		rowValues.putAll(decomposer.decompose(CustomStrings.SAMP_MAT_CODE_COL, row.getCode(CustomStrings.SAMP_MAT_CODE_COL)));

		// extract psu id for cwd
		rowValues.putAll(decomposer.decompose(CustomStrings.SAMP_UNIT_IDS_COL, row.getCode(CustomStrings.SAMP_UNIT_IDS_COL)));

		// extract prog info
		rowValues.putAll(decomposer.decompose(CustomStrings.PROG_INFO_COL, row.getCode(CustomStrings.PROG_INFO_COL)));

		// extract the allele if RGT
		if (isRGT)
			rowValues.putAll(decomposer.decompose(CustomStrings.PARAM_CODE_COL, row.getCode(CustomStrings.PARAM_CODE_COL)));

		// copy values into the summarized information
		SummarizedInfo summInfo = new SummarizedInfo(row);
		for (String key : rowValues.keySet()) {
			summInfo.put(key, rowValues.get(key));
		}

		// set the report as parent of the summ info
		Relation.injectParent(report1, summInfo);

		// add pref and settings as information
		Relation.injectGlobalParent(summInfo, CustomStrings.PREFERENCES_SHEET, daoService);
		Relation.injectGlobalParent(summInfo, CustomStrings.SETTINGS_SHEET, daoService);

		// set also the summarized information type using
		// the species
		String type = summInfo.getTypeBySpecies();
		summInfo.setType(type);
		return summInfo;
	}

	/**
	 * Extract the case row from the analytical result row
	 * 
	 * @param report1
	 * @param summInfo
	 * @param row
	 * @return
	 * @throws FormulaException
	 * @throws ParseException
	 */
	private TableRow extractCase(TseReport report1, SummarizedInfo summInfo, TableRow row) throws ParseException {
		// set schema (required for next step), we are processing a result row,
		// even if we are extracting the case information data!
		row.setSchema(TableSchemaList.getByName(CustomStrings.RESULT_SHEET));

		TableCell sampId = row.get(CustomStrings.SAMPLE_ID_COL);
		if (sampId == null) {
			LOGGER.error("Missing sampId to extract from case row: ", row);
			throw new ParseException("Missing sampId", -1);
		}

		// create empty case report
		TableRow caseReport;

		// if not already added
		if (cases.get(sampId.getLabel()) == null) {
			// create the case info (we do not copy the data, since this row
			// is actually an analytical result and we just need to
			// extract the relevant information)
			caseReport = new TableRow(TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET));

			HashMap<String, TableCell> rowValues = new HashMap<>();
			TSEFormulaDecomposer decomposer = new TSEFormulaDecomposer();

			// get decomposed values
			for (String id : new String[] { CustomStrings.EVAL_INFO_COL, CustomStrings.SAMP_UNIT_IDS_COL,
					CustomStrings.SAMP_EVENT_INFO_COL, CustomStrings.SAMP_MAT_INFO_COL,
					CustomStrings.SAMP_MAT_CODE_COL }) {
				rowValues.putAll(decomposer.decompose(id, row.getCode(id)));
			}

			// manually convert eval info com into evalCom
			TableCell evalCom = decomposer
					.decompose(CustomStrings.EVAL_INFO_COL, row.getCode(CustomStrings.EVAL_INFO_COL))
					.get(CustomStrings.EVAL_COMMENT_BREED_ATTRIBUTE_NAME);
			rowValues.put(CustomStrings.EVAL_COMMENT_COL, evalCom);

			// manually convert samp event info into breed
			TableCell breed = decomposer
					.decompose(CustomStrings.SAMP_MAT_INFO_COL, row.getCode(CustomStrings.SAMP_MAT_INFO_COL))
					.get(CustomStrings.EVAL_COMMENT_BREED_ATTRIBUTE_NAME);
			rowValues.put(CustomStrings.BREED_COL, breed);

			// save sample id
			rowValues.put(CustomStrings.SAMPLE_ID_COL, sampId);
			rowValues.put(CustomStrings.SAMP_AREA_COL, row.get(CustomStrings.SAMP_AREA_COL));
			rowValues.put(CustomStrings.SAMP_DAY_COL, row.get(CustomStrings.SAMP_DAY_COL));

			// store all the values into the case report
			for (String key : rowValues.keySet()) {
				caseReport.put(key, rowValues.get(key));
			}

			// set the report/summ info as parent of case report
			Relation.injectParent(report1, caseReport);
			Relation.injectParent(summInfo, caseReport);
		} else {
			// else if already present, get it from the cache
			caseReport = cases.get(sampId.getLabel());
		}
		return caseReport;
	}

	/**
	 * Extract the analytical result data from the current row
	 * 
	 * @param report
	 * @param summInfo
	 * @param caseInfo
	 * @param row
	 * @return
	 * @throws ParseException
	 */
	private static TableRow extractAnalyticalResult(TseReport report, SummarizedInfo summInfo, TableRow caseInfo,
			TableRow row) throws ParseException {
		// set the summarised information schema
		row.setSchema(TableSchemaList.getByName(CustomStrings.RESULT_SHEET));

		// decompose param code
		TSEFormulaDecomposer decomposer = new TSEFormulaDecomposer();
		HashMap<String, TableCell> rowValues = decomposer.decompose(CustomStrings.PARAM_CODE_COL, row.getCode(CustomStrings.PARAM_CODE_COL));
		rowValues.putAll(decomposer.decompose(CustomStrings.SAMP_INFO_COL, row.getCode(CustomStrings.SAMP_INFO_COL)));

		// save also the test aim with base term and test result
		String paramBaseTerm = decomposer.getBaseTerm(row.getCode(CustomStrings.PARAM_CODE_COL));

		// save the base term also
		row.put(CustomStrings.PARAM_CODE_BASE_TERM_COL, paramBaseTerm);

		String resQualValue = row.getCode(CustomStrings.RES_QUAL_VALUE_COL);
		// only if we have the test result put also the test aim
		if (!resQualValue.isEmpty()) {
			String testAim = paramBaseTerm + "$" + resQualValue;
			row.put(CustomStrings.TEST_AIM_COL, testAim);
		}

		// copy values into the row
		TableRow result = new TableRow(row);
		for (String key : rowValues.keySet()) {
			result.put(key, rowValues.get(key));
		}

		// set the report as parent of the summ info
		Relation.injectParent(report, result);
		Relation.injectParent(summInfo, result);
		Relation.injectParent(caseInfo, result);
       
		return result;
	}

	/**
	 * Given a prog id of an analytical result, get the summarized information which
	 * is related to it
	 * 
	 * @param resultOrigSampId
	 * @return
	 * @throws FormulaException
	 */
	private SummarizedInfo getSummInfoByOrigSampId(String resultOrigSampId) throws FormulaException {
		for (SummarizedInfo info : summInfos) {
			String sampId = reportService.getSampId(info);
			if (sampId.equals(resultOrigSampId)) {
				return info;
			}
		}
        LOGGER.info("Summarized information to given a prog id of an analytical result could not be found");
		return null;
	}

	@Override
	public TableRow importDatasetMetadata(Dataset dataset) {
		// extract the information from the dataset
		// and insert the report into the database
		this.mainReport = reportService.reportFromDataset(dataset);
		daoService.add(mainReport);
		return this.mainReport;
	}

	@Override
	public void importDatasetRows(List<TableRow> rows) throws FormulaException, ParseException {
		LOGGER.info("Importing the summarized information: ", rows);

		// first import the summarized information
		importSummarizedInformation(mainReport, rows);

		// catch the exception when importing old reports
		try {
			// then import cases and results
			importCasesAndResults(mainReport, rows);
		} catch (Exception e) {
			LOGGER.error("Error upon importing cases and results", e);
			e.printStackTrace();
		}
	}
}
