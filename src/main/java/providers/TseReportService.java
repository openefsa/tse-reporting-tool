package providers;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dataset.Dataset;
import dataset.IDataset;
import dataset.RCLDatasetStatus;
import formula.Formula;
import formula.FormulaDecomposer;
import formula.FormulaException;
import formula.FormulaSolver;
import message.MessageConfigBuilder;
import report.Report;
import report_downloader.TSEFormulaDecomposer;
import soap_interface.IGetAck;
import soap_interface.IGetDataset;
import soap_interface.IGetDatasetsList;
import soap_interface.ISendMessage;
import table_relations.Relation;
import table_skeleton.*;
import tse_analytical_result.AnalyticalResult;
import tse_case_report.CaseReport;
import tse_config.CustomStrings;
import tse_report.TseReport;
import tse_summarized_information.SummarizedInfo;
import tse_validator.CaseReportValidator;
import tse_validator.ResultValidator;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;
import xlsx_reader.TableHeaders.XlsxHeader;

import static tse_config.CustomStrings.RES_ID_COL;

/**
 * 
 * Report service
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class TseReportService extends ReportService {
	private static final Logger LOGGER = LogManager.getLogger(TseReportService.class);

	public enum RowType {
		SUMM, CASE, RESULT
	}

	private final IFormulaService formulaService1;

	public TseReportService(IGetAck getAck, IGetDatasetsList<IDataset> getDatasetsList, ISendMessage sendMessage,
			IGetDataset getDataset, ITableDaoService daoService, IFormulaService formulaService) {
		super(getAck, getDatasetsList, sendMessage, getDataset, daoService, formulaService);
		this.formulaService1 = formulaService;
	}

	/**
	 * get sampId field in row
	 * 
	 * @author shahaal
	 * @param summInfo
	 * @return
	 * @throws FormulaException
	 */
	public String getSampId(SummarizedInfo summInfo) throws FormulaException {
		// we need all the fields to compute the context id, in order to
		// solve formula dependencies
		FormulaSolver solver = new FormulaSolver(summInfo, daoService);
		ArrayList<Formula> formulas = solver.solveAll(XlsxHeader.LABEL_FORMULA.getHeaderName());
		LOGGER.info("Formulas: ", Arrays.asList(formulas));

		for (Formula f : formulas) {
			if (f.getColumn().getId().equals(CustomStrings.SAMPLE_ID_COL))
				return f.getSolvedFormula();
		}
		return null;
	}

	/**
	 * Check if the analytical result is related to random genotyping
	 * 
	 * @param row
	 * @return
	 * @throws ParseException
	 */
	public static boolean isRGTResult(TableRow row) throws ParseException {
		FormulaDecomposer decomposer = new FormulaDecomposer();
		String paramBaseTerm = decomposer.getBaseTerm(row.getCode(CustomStrings.PARAM_CODE_COL));

		boolean rgtParamCode = paramBaseTerm.equals(CustomStrings.RGT_PARAM_CODE);

		LOGGER.info("Analytical result is related to random genotyping: {}", rgtParamCode);
		return rgtParamCode;
	}

	/**
	 * Get the type of the row
	 * 
	 * @param row
	 * @return
	 */
	public static RowType getRowType(TableRow row) {
		RowType type = null;
		switch (row.getSchema().getSheetName()) {
			case CustomStrings.SUMMARIZED_INFO_SHEET:	type = RowType.SUMM;	break;
			case CustomStrings.CASE_INFO_SHEET:			type = RowType.CASE;	break;
			case CustomStrings.RESULT_SHEET:			type = RowType.RESULT;	break;
		}
		LOGGER.debug("Row type: {}", type);
		return type;
	}

	/**
	 * Extract the origSampId from analytical result
	 * 
	 * @author shahaal
	 * @param result
	 * @return
	 * @throws ParseException
	 * @throws FormulaException
	 */
	public static String getOrigSampIdFrom(TableRow result) throws ParseException, FormulaException {
		// decompose param code
		TSEFormulaDecomposer decomposer = new TSEFormulaDecomposer();
		HashMap<String, TableCell> rowValues = decomposer.decompose(CustomStrings.SAMP_INFO_COL, result.getCode(CustomStrings.SAMP_INFO_COL));

		// get the cell for origSampId
		TableCell cell = rowValues.get(CustomStrings.ORIG_SAMP_ID_COL);

		// if the cell is null (old report) then retrieve it from resId
		if (cell == null) {
			// return the substring if dot present
			String[] split = result.getCode(RES_ID_COL).split("\\.");
			
			// @TODO to better check, what happens if split not possible
			if (split.length >= 1)
				return split[0];
		}
		
		LOGGER.info("Samp orig id=" + cell.getCode() + " for " + result);
		// return the sampOrigId
		return cell.getCode();
	}

	/**
	 * Get all the elements of the report (summ info, case, analytical results)
	 * 
	 * @return
	 */
	public ArrayList<TableRow> getAllRecords(TseReport report) {
		// children schemas
		TableSchema[] schemas = new TableSchema[] {
				TableSchemaList.getByName(CustomStrings.SUMMARIZED_INFO_SHEET),
				TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET),
				TableSchemaList.getByName(CustomStrings.RESULT_SHEET)
		};

		return getRecords(report, schemas);
	}

	public ArrayList<TableRow> getRecords(TseReport report, TableSchema[] schemas) {
		ArrayList<TableRow> records = new ArrayList<>();

		// for each child schema get the rows related to the report
		for (TableSchema schema : schemas) {
			ArrayList<TableRow> children = getDaoService().getByParentId(schema, CustomStrings.REPORT_SHEET, report.getDatabaseId(), true, "desc");
			if (children != null)
				records.addAll(children);
		}
		return records;
	}

	/**
	 * Check if a row has children or not
	 * 
	 * @param parent
	 * @param childSchema
	 * @return
	 */
	public boolean hasChildren(TableRow parent, TableSchema childSchema) {
		return !getDaoService()
				.getByParentId(childSchema, parent.getSchema().getSheetName(), parent.getDatabaseId(), false)
				.isEmpty();
	}

	public void updateChildrenErrors(SummarizedInfo summInfo) {
		boolean errors = false;
		CaseReportValidator validator = new CaseReportValidator(getDaoService());

		TableRowList rowsByParent = getDaoService().getByParentId(TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET), summInfo.getSchema().getSheetName(), summInfo.getDatabaseId(), true);
		for (TableRow row : rowsByParent) {
			if (validator.getOverallWarningLevel(row) > 0) {
				summInfo.setChildrenError();
				errors = true;
				break;
			}
		}

		if (!errors) {
			summInfo.removeChildrenError();
		}
		getDaoService().update(summInfo);
	}

	public void updateChildrenErrors(CaseReport caseReport) {
		boolean error = false;
		ResultValidator resultValidator = new ResultValidator();
		TableRowList rowsByParentId = getDaoService().getByParentId(TableSchemaList.getByName(CustomStrings.RESULT_SHEET), caseReport.getSchema().getSheetName(), caseReport.getDatabaseId(), true);
		for (TableRow r : rowsByParentId) {
			if (resultValidator.getWarningLevel(r) > 0) {
				caseReport.setChildrenError();
				error = true;
				break;
			}
		}

		if (!error) {
			caseReport.removeChildrenError();
		}
		getDaoService().update(caseReport);
	}

	public MessageConfigBuilder getSendMessageConfiguration(TseReport report) {
		Collection<TableRow> messageParents = new ArrayList<>();
		messageParents.add(report);

		// add the settings data
		try {
			TableRow settings = Relation.getGlobalParent(CustomStrings.SETTINGS_SHEET, getDaoService());
			messageParents.add(settings);
		} catch (Exception e) {
			LOGGER.error("Error in setting message data", e);
			e.printStackTrace();
		}

		return new MessageConfigBuilder(formulaService1, messageParents);
	}

	public MessageConfigBuilder getAcceptDwhBetaMessageConfiguration(TseReport report) {
		Collection<TableRow> messageParents = new ArrayList<>();
		messageParents.add(report);

		// add the settings data
		try {
			TableRow settings = Relation.getGlobalParent(CustomStrings.SETTINGS_SHEET, getDaoService());
			messageParents.add(settings);
		} catch (Exception e) {
			LOGGER.error("Error in setting Accept Dwh Beta message data", e);
			e.printStackTrace();
		}

		return new MessageConfigBuilder(formulaService1, messageParents);
	}

	/**
	 * Create a new version of the report and save it into the database. The version
	 * is automatically increased
	 * 
	 * @return the amended report
	 */
	public TseReport amend(TseReport report) {
		TseReport amendedReport = new TseReport();
		amendedReport.copyValues(report);
		amendTseReport(amendedReport, report);
		TseReport tseReport = copyReportChildren(amendedReport, report, false);
		LOGGER.info("Amended report : {}", report.getDatabaseId());
		return tseReport;
	}

	/**
	 * Copy a report and then generate the required fields of the new report
	 *
	 * @param source the report being copied
	 * @param target the report that will contain the copied data
	 * @return the copied report
	 */
	public TseReport copyReport(TseReport source, TseReport target) {
		List<Relation> directChildren = target.getSchema().getDirectChildren();
		for (Relation rel: directChildren) {
			daoService.deleteByParentId(rel.getChildSchema(), target.getSchema().getSheetName(), target.getDatabaseId());
		}

		target.setStatus(RCLDatasetStatus.DRAFT);
		target.setMessageId("");
		daoService.update(target);

		copyReportChildren(source, target, true);
		LOGGER.info("Imported report {} to {}", source.getDatabaseId(), target.getDatabaseId());
		return target;
	}

	/**
	 * Copy a report's child items (SummarizedInfo, CaseInfo and AnalyticalResults of a report).
	 * Does not copy the report itself. This should be handled in the caller method.
	 * If init is true, will also use formulas to generate the fields instead of simply copying them
	 *
	 * @param source the report being copied
	 * @param target the report that will contain the copied data
	 * @param init determines if the report's formula fields will be generated again or copied from source
	 *
	 * @return
	 */
	private TseReport copyReportChildren(TseReport source, TseReport target, boolean init) {
		Stack<TableRow> elements = new Stack<>();
		elements.add(source);

		SummarizedInfo summInfo = null;
		CaseReport caseReport = null;
		while (!elements.isEmpty()) {
			TableRow currentElement = elements.pop();
			TableSchema schema = currentElement.getSchema();
			boolean isSumm = schema.equals(TableSchemaList.getByName(CustomStrings.SUMMARIZED_INFO_SHEET));
			boolean isCase = schema.equals(TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET));
			boolean isRslt = schema.equals(TableSchemaList.getByName(CustomStrings.RESULT_SHEET));

			// only one or none direct child exists
			List<Relation> directChildren = schema.getDirectChildren();
			TableSchema childSchema = directChildren.size() > 0 ? directChildren.get(0).getChildSchema() : null;

			// get the element children (before changing its id)
			Collection<TableRow> children = null;
			if (!isRslt) {
				children = getDaoService().getByParentId(childSchema, schema.getSheetName(), currentElement.getDatabaseId(), true);
			}

			if (isSumm) {
				summInfo = initSummarizedInfo(currentElement, init, target);
			} else if (isCase) {
				caseReport = initCaseReport(currentElement, target, summInfo);
			} else if (isRslt) {
				copyAnalyticalResult(currentElement, init, target, summInfo, caseReport);
			}

			// add the children
			if (!isRslt)
				elements.addAll(children);
		}
		return target;
	}

	private void amendTseReport(TseReport source, TseReport target) {
		// increase version starting from the current
		String newVersion = TableVersion.createNewVersion(source.getVersion());
		target.setVersion(newVersion);

		target.setStatus(RCLDatasetStatus.DRAFT);
		target.setId("");
		target.setMessageId("");
		getDaoService().add(target);
	}

	private SummarizedInfo initSummarizedInfo(TableRow currentElement, boolean init, TseReport target) {
		return init ? copyTableRowWithFormulaUpdate(new SummarizedInfo(), currentElement, target)
					: copyTableRow(new SummarizedInfo(), currentElement, target);
	}

	private CaseReport initCaseReport(TableRow currentElement, TableRow... parents) {
		return copyTableRow(new CaseReport(), currentElement, parents);
	}

	private AnalyticalResult copyAnalyticalResult(TableRow currentElement, boolean init, TableRow... parents) {
		return init ? copyTableRowWithFormulaUpdate(new AnalyticalResult(), currentElement, parents)
					: copyTableRow(new AnalyticalResult(), currentElement, parents);
	}

	/**
	 * Copy values from source to target and inject new parent ids
	 *
	 * @param target  the new row being added
	 * @param source  the source row to get values
	 * @param parents the parent tables to inject in the target
	 *
	 * @return the target row with updated values
	 */
	private <T extends TableRow> T copyTableRow(T target, TableRow source, TableRow... parents) {
		target.copyValues(source);

		Relation.injectGlobalParent(target, CustomStrings.SETTINGS_SHEET);
		Relation.injectGlobalParent(target, CustomStrings.PREFERENCES_SHEET);
		for (TableRow tr : parents) {
			Relation.injectParent(tr, target);
		}

		getDaoService().add(target);
		return target;
	}

	/**
	 * Copies one row into another like {@link #copyTableRow(TableRow, TableRow, TableRow...)} but also initialises
	 * the fields that are automatically generated or are generated by formulas
	 *
	 * @param target  the new row being added
	 * @param source  the source row to get values
	 * @param parents the parent tables to inject in the target
	 *
	 * @return the target row with updated values
	 */
	private <T extends TableRow> T copyTableRowWithFormulaUpdate(T target, TableRow source, TableRow... parents) {
		Relation.injectGlobalParent(target, CustomStrings.SETTINGS_SHEET);
		Relation.injectGlobalParent(target, CustomStrings.PREFERENCES_SHEET);
		for (TableRow tr : parents) {
			Relation.injectParent(tr, target);
		}
		target.save();

		// Initialise the target fields with default values
		target.Initialise();
		// insert the target and save also the target id
		target.update();
		// Initialise the formulas with target id
		target.Initialise();

		// fill editable fields
		for (TableColumn col : target.getSchema()) {
			String id = col.getId();
			// resId must not be copied but generated as we do not know if it was changed manually
			// type is the only none editable field that needs to be copied
			if ((col.isEditable(target) && !id.equals("resId")) || id.equals("type")) {
				target.put(id, source.get(id));
			}
		}

		// update the formulas
		target.updateFormulas();
		// update the target with the formulas solved
		target.update();

		return target;
	}

	/**
	 * Create a report from a dataset
	 * 
	 * @param dataset
	 * @return
	 */
	public TseReport reportFromDataset(Dataset dataset) {
		TseReport report = new TseReport();
		report.setId(dataset.getId());

		String senderDatasetId = dataset.getOperation().getSenderDatasetId();
		String[] split = Dataset.splitSenderId(senderDatasetId);
		String senderId = senderDatasetId;

		if (split != null && split.length > 1) {
			senderId = split[0];
			report.setVersion(split[1]);
		} else {
			report.setVersion(TableVersion.getFirstVersion());
		}

		report.setSenderId(senderId);
		report.setStatus(dataset.getRCLStatus() != null ? dataset.getRCLStatus() : RCLDatasetStatus.DRAFT);

		// split FR1705... into country year and month
		if (senderId.length() < 6) {
			LOGGER.error("Report#fromDataset Cannot parse sender dataset id, expected at least 6 characters, found {}", senderId);
			report.setCountry("");
			report.setYear("");
			report.setMonth("");
		} else {
			String countryCode = senderDatasetId.substring(0, 2);
			String year = "20" + senderDatasetId.substring(2, 4);
			String month = senderDatasetId.substring(4, 6);

			// remove the padding
			if (month.substring(0, 1).equals("0"))
				month = month.substring(1, 2);

			report.setCountry(countryCode);
			report.setYear(year);
			report.setMonth(month);
		}

		// copy message ids
		report.setMessageId(dataset.getLastMessageId());
		report.setLastMessageId(dataset.getLastMessageId());
		report.setLastModifyingMessageId(dataset.getLastModifyingMessageId());
		report.setLastValidationMessageId(dataset.getLastValidationMessageId());

		// add the preferences
		Relation.injectGlobalParent(report, CustomStrings.PREFERENCES_SHEET, getDaoService());

		// shahaal: removed stmt since the report is not based anymore on the
		// exceptional country
		// but instead on the CWD EXTENDED CONTEXT
		/*
		 * try {
		 * 
		 * //uncomment for printing the columns of the schema in the report //for
		 * (TableColumn tc : report.getSchema()) // System.out.println("Column: " +
		 * tc.getId());
		 * 
		 * String isCWDExtendedContext = formulaService.solve(report,
		 * report.getSchema()..getById(CustomStrings.EXCEPTION_COUNTRY_COL),
		 * XlsxHeader.LABEL_FORMULA);
		 * 
		 * report.setCWDExtendedContext(isCWDExtendedContext);
		 * 
		 * } catch (FormulaException e) { e.printStackTrace(); }
		 */
        LOGGER.info("The report creation by this dataset :" + dataset + "is completed!", report);
		return report;

	}

	public void createDefaultRGTCase(Report report, TableRow summInfo) {
		TableSchema caseSchema = TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET);
		TableRow resultRow = new TableRow(caseSchema);

		// inject the case parent to the result
		Relation.injectParent(report, resultRow);
		Relation.injectParent(summInfo, resultRow);

		formulaService1.Initialise(resultRow);

		// add get the id and update the fields
		daoService.add(resultRow);

		formulaService1.Initialise(resultRow);
		resultRow.put(CustomStrings.PART_COL, CustomStrings.BLOOD_CODE);

		daoService.update(resultRow);
	}

	/**
	 * Once a summ info is clicked, create the default cases according to number of
	 * positive/inconclusive cases
	 * 
	 * @param summInfo
	 * @throws IOException
	 */
	public void createDefaultCases(Report report, TableRow summInfo) throws IOException {
		// check cases number
		int positive = summInfo.getNumLabel(CustomStrings.TOT_SAMPLE_POSITIVE_COL);
		int inconclusive = summInfo.getNumLabel(CustomStrings.TOT_SAMPLE_INCONCLUSIVE_COL);

		TableSchema resultSchema = TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET);

		boolean isCervid = summInfo.getCode(CustomStrings.SUMMARIZED_INFO_TYPE).equals(CustomStrings.SUMMARIZED_INFO_CWD_TYPE);

		// for cervids we need double rows
		int repeats = isCervid ? 2 : 1;

		// for each inconclusive
		for (int i = 0; i < inconclusive; ++i) {

			for (int j = 0; j < repeats; ++j) {
				TableRow resultRow = new TableRow(resultSchema);

				// inject the case parent to the result
				Relation.injectParent(report, resultRow);
				Relation.injectParent(summInfo, resultRow);
				formulaService1.Initialise(resultRow);

				// add result
				daoService.add(resultRow);

				formulaService1.Initialise(resultRow);

				// set assessment as inconclusive
				TableCell value = new TableCell();
				value.setCode(CustomStrings.DEFAULT_ASSESS_INC_CASE_CODE);
				value.setLabel(CustomStrings.DEFAULT_ASSESS_INC_CASE_LABEL);
				resultRow.put(CustomStrings.SAMP_EVENT_ASSES_COL, value);

				// default always obex
				resultRow.put(CustomStrings.PART_COL, CustomStrings.OBEX_CODE);

				if (isCervid) {
					if (j == 0) {
						resultRow.put(CustomStrings.PART_COL, CustomStrings.OBEX_CODE);
					} else if (j == 1) {
						resultRow.put(CustomStrings.PART_COL, CustomStrings.RETROPHARYNGEAL_CODE);
					}
				}

				daoService.update(resultRow);
			}
		}

		// for each positive
		for (int i = 0; i < positive; ++i) {
			for (int j = 0; j < repeats; ++j) {
				TableRow resultRow = new TableRow(resultSchema);

				// inject the case parent to the result
				Relation.injectParent(report, resultRow);
				Relation.injectParent(summInfo, resultRow);
				formulaService1.Initialise(resultRow);

				// add get the id and update the fields
				daoService.add(resultRow);

				// default always obex
				resultRow.put(CustomStrings.PART_COL, CustomStrings.OBEX_CODE);

				if (isCervid) {
					if (j == 0) {
						resultRow.put(CustomStrings.PART_COL, CustomStrings.OBEX_CODE);
					} else if (j == 1) {
						resultRow.put(CustomStrings.PART_COL, CustomStrings.RETROPHARYNGEAL_CODE);
					}
				}

				daoService.update(resultRow);
			}
		}
	}

	public TableRowList createDefaultResults(Report report, SummarizedInfo summInfo, CaseReport caseInfo) throws IOException {
		PredefinedResultService r = new PredefinedResultService(daoService, formulaService1);
		TableRowList results = r.createDefaultResults(report, summInfo, caseInfo);
		return results;
	}
}
