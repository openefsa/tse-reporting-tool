package providers;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import predefined_results.PredefinedResult;
import predefined_results.PredefinedResultHeader;
import predefined_results.PredefinedResultList;
import report.Report;
import table_relations.Relation;
import table_skeleton.TableRow;
import table_skeleton.TableRowList;
import tse_analytical_result.AnalyticalResult;
import tse_case_report.CaseReport;
import tse_config.CustomStrings;
import tse_summarized_information.SummarizedInfo;

/**
 * Class which models a generic result for the specific case
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class PredefinedResultService {
	private static final Logger LOGGER = LogManager.getLogger(PredefinedResultService.class);
	
	private final ITableDaoService daoService;
	private final IFormulaService formulaService;
	
	public PredefinedResultService(ITableDaoService daoService, IFormulaService formulaService) {
		this.daoService = daoService;
		this.formulaService = formulaService;
	}
	
	/**
	 * Create the default results for a case
	 * @param report
	 * @param summInfo
	 * @param caseReport
	 * @throws IOException
	 */
	public TableRowList createDefaultResults(Report report, SummarizedInfo summInfo, CaseReport caseReport) {
		TableRowList results = new TableRowList();
		
		AnalyticalResult r = createDefaultResult(report, summInfo, caseReport, 
				PredefinedResultHeader.SCREENING,
				CustomStrings.SCREENING_TEST_CODE);
		if (r != null)
			results.add(r);
		
		r = createDefaultResult(report, summInfo, caseReport, 
				PredefinedResultHeader.CONFIRMATORY, 
				CustomStrings.CONFIRMATORY_TEST_CODE);
		if (r != null)
			results.add(r);
		
		// create discriminatory predefined results only for non CWD records
		if(!summInfo.isCWD()) {
			r = createDefaultResult(report, summInfo, caseReport, 
					PredefinedResultHeader.DISCRIMINATORY,
					CustomStrings.DISCRIMINATORY_TEST_CODE);
			if (r != null)
				results.add(r);
		}
		
		r = createDefaultResult(report, summInfo, caseReport, 
				PredefinedResultHeader.GENOTYPING_BASE_TERM,
				CustomStrings.MOLECULAR_TEST_CODE);
		if (r != null)
			results.add(r);
		
		return results;
	}
	
	/**
	 * Check if the preference for the confirmatory test was set for
	 * the selected type of animal
	 * @param type
	 * @return
	 * @throws IOException
	 */
	public boolean isConfirmatoryTested(String type) {
		String column;
		switch(type) {
			case CustomStrings.SUMMARIZED_INFO_BSE_TYPE:
				column = CustomStrings.PREFERENCES_CONFIRMATORY_BSE;
				break;
			case CustomStrings.SUMMARIZED_INFO_SCRAPIE_TYPE:
				column = CustomStrings.PREFERENCES_CONFIRMATORY_SCRAPIE;
				break;
			case CustomStrings.SUMMARIZED_INFO_CWD_TYPE:
				column = CustomStrings.PREFERENCES_CONFIRMATORY_CWD;
				break;
			case CustomStrings.SUMMARIZED_INFO_BSEOS_TYPE:
				column = CustomStrings.PREFERENCES_CONFIRMATORY_BSE_OS;
				break;
			default:
				return false;
		}
		return ! Relation.getGlobalParent(CustomStrings.PREFERENCES_SHEET, daoService)
					.getCode(column)
					.isEmpty();
	}
	
	public static String getPreferredTestType(String testType) {
		return Relation.getGlobalParent(CustomStrings.PREFERENCES_SHEET).getCode(testType);
	}

	public static String getPreferredTestType(String recordType, String testType) {
		String preferredTestType = null;
		
		switch(testType) {
		case CustomStrings.SCREENING_TEST_CODE:
			switch(recordType) {
			case CustomStrings.SUMMARIZED_INFO_BSE_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_SCREENING_BSE);
				break;
			case CustomStrings.SUMMARIZED_INFO_SCRAPIE_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_SCREENING_SCRAPIE);
				break;
			case CustomStrings.SUMMARIZED_INFO_CWD_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_SCREENING_CWD);
				break;
			case CustomStrings.SUMMARIZED_INFO_BSEOS_TYPE:
					preferredTestType = getPreferredTestType(
							CustomStrings.PREFERENCES_SCREENING_BSE_OS);
					break;
			default:
				break;
			}
			break;
		case CustomStrings.CONFIRMATORY_TEST_CODE:
			switch(recordType) {
			case CustomStrings.SUMMARIZED_INFO_BSE_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_CONFIRMATORY_BSE);
				break;
			case CustomStrings.SUMMARIZED_INFO_SCRAPIE_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_CONFIRMATORY_SCRAPIE);
				break;
			case CustomStrings.SUMMARIZED_INFO_CWD_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_CONFIRMATORY_CWD);
				break;
			case CustomStrings.SUMMARIZED_INFO_BSEOS_TYPE:
				preferredTestType = getPreferredTestType(
						CustomStrings.PREFERENCES_CONFIRMATORY_BSE_OS);
				break;
			default:
				break;
			}
			break;
		case CustomStrings.DISCRIMINATORY_TEST_CODE:
			switch(recordType) {
			case CustomStrings.SUMMARIZED_INFO_BSE_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_DISCRIMINATORY_BSE);
				break;
			case CustomStrings.SUMMARIZED_INFO_SCRAPIE_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_DISCRIMINATORY_SCRAPIE);
				break;
			case CustomStrings.SUMMARIZED_INFO_CWD_TYPE:
				preferredTestType = getPreferredTestType( 
						CustomStrings.PREFERENCES_DISCRIMINATORY_CWD);
				break;
			case CustomStrings.SUMMARIZED_INFO_BSEOS_TYPE:
				preferredTestType = getPreferredTestType(
						CustomStrings.PREFERENCES_DISCRIMINATORY_BSE_OS);
				break;
			default:
				break;
			}
			break;
		case CustomStrings.MOLECULAR_TEST_CODE:
			preferredTestType = CustomStrings.AN_METH_CODE_GENOTYPING;
			break;
		default:
			break;
		}
		
		return preferredTestType;
	}
	
	public PredefinedResult getPredefinedResult(SummarizedInfo summInfo, TableRow caseReport) {
		// put the predefined value for the param code and the result
		PredefinedResultList predResList = PredefinedResultList.getAll();

		// get the info to know which result should be created
		String recordType = summInfo.getCode(CustomStrings.SUMMARIZED_INFO_TYPE);
		String source = summInfo.getCode(CustomStrings.SOURCE_COL);
		String sampEventAsses = caseReport.getCode(CustomStrings.SAMP_EVENT_ASSES_COL);
		boolean confirmatoryTested = isConfirmatoryTested(recordType);

		// get the default value
		PredefinedResult defaultResult = predResList.get(recordType, source, confirmatoryTested, sampEventAsses);
		
		LOGGER.info("PredefinedResult: ", defaultResult);
		return defaultResult;
	}
	
	/**
	 * Create a default result for the selected test
	 * @param report
	 * @param summInfo
	 * @param caseReport
	 * @param testTypeCode
	 * @throws IOException
	 */
	private AnalyticalResult createDefaultResult(Report report, SummarizedInfo summInfo, TableRow caseReport,
			PredefinedResultHeader test, String testTypeCode) {
		
		AnalyticalResult resultRow = new AnalyticalResult();
		
		// inject the case parent to the result
		Relation.injectParent(report, resultRow);
		Relation.injectParent(summInfo, resultRow);
		Relation.injectParent(caseReport, resultRow);

		// get the default value
		PredefinedResult defaultResult = getPredefinedResult(summInfo, caseReport);
		
		// add the param base term and the related default result
		boolean added = addParamAndResult(resultRow, defaultResult, test);
		
		if (added) {
			// code crack to save the row id for the resId field
			// otherwise its default value will be corrupted
			daoService.add(resultRow);
			formulaService.Initialise(resultRow);
			
			resultRow.put(CustomStrings.AN_METH_TYPE_COL, testTypeCode);
			
			// get the info to know which result should be created
			String recordType = summInfo.getCode(CustomStrings.SUMMARIZED_INFO_TYPE);
			
			// add also the preferred test type
			String prefTest = getPreferredTestType(recordType, testTypeCode);
			
			if (prefTest != null)
				resultRow.put(CustomStrings.AN_METH_CODE_COL, prefTest);
			else
				LOGGER.warn("No preferred value of anMethCode was found for anMethType " + testTypeCode);
			
			addParamAndResult(resultRow, defaultResult, test);
			
			formulaService.updateFormulas(resultRow);
			daoService.update(resultRow);
			
			return resultRow;
		}
		LOGGER.info("No default result for this particular test has been created: ", resultRow, defaultResult, test);
		return null;
	}
	
	
	/**
	 * Add the param and the result to the selected row
	 * @param result
	 * @param defValues
	 * @param codeCol
	 * @return
	 */
	private boolean addParamAndResult(TableRow result, PredefinedResult defValues, PredefinedResultHeader codeCol) {
		String code = defValues.get(codeCol);
		
		// put the test aim
		if (codeCol != PredefinedResultHeader.GENOTYPING_BASE_TERM && code != null && !code.equals("null"))  // excel fix
			result.put(CustomStrings.TEST_AIM_COL, code);
		
		// extract from it the param code and the result
		// and add them to the row
		return addParamAndResult(result, code);
	}
	
	/**
	 * Add param and result. These values are taken from the test code
	 * which is composed of paramBaseTerm$resultValue
	 *
	 * @param result
	 * @param testCode
	 * @return
	 */
	public static boolean addParamAndResult(TableRow result, String testCode) {
		if (testCode == null)
			return false;
		
		String[] split = testCode.split("\\$");
		String paramBaseTerm = split[0];
		
		String resultValue = null;
		if (split.length > 1)
			resultValue = split[1];
		
		boolean added = false;
		if (paramBaseTerm != null) {
			result.put(CustomStrings.PARAM_CODE_BASE_TERM_COL, paramBaseTerm);
			
			added = true;
			
			if (resultValue != null) {
				result.put(CustomStrings.RES_QUAL_VALUE_COL, resultValue);
			}
		}
		
		return added;
	}
}
