package tse_analytical_result;

import predefined_results.PredefinedResult;
import predefined_results.PredefinedResultHeader;
import providers.PredefinedResultService;
import table_dialog.EditorListener;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;
import tse_case_report.CaseReport;
import tse_config.CustomStrings;
import tse_summarized_information.SummarizedInfo;

class ResultEditorListener implements EditorListener {

    private final PredefinedResultService resultService;

    private final SummarizedInfo summInfo;
    private final CaseReport caseInfo;

    public ResultEditorListener(PredefinedResultService resultService, SummarizedInfo summInfo, CaseReport caseInfo) {
        this.summInfo = summInfo;
        this.resultService = resultService;
        this.caseInfo = caseInfo;
    }

    @Override
    public void editStarted() {
    }

    @Override
    public void editEnded(TableRow row, TableColumn field, boolean changed) {
        if (changed) {
            // reset the testaim and the anmethcode if anmethtype is changed
            String fieldId = field.getId();
            if (fieldId.equals(CustomStrings.AN_METH_TYPE_COL)) {
                row.Initialise(CustomStrings.TEST_AIM_COL);
                row.Initialise(CustomStrings.AN_METH_CODE_COL);
            }

            // update the baseterm and the result value if testaim changes
            if (fieldId.equals(CustomStrings.TEST_AIM_COL) || fieldId.equals(CustomStrings.AN_METH_CODE_COL)) {

                // if genotyping set base term
                if (row.getCode(CustomStrings.AN_METH_CODE_COL).equals(CustomStrings.AN_METH_CODE_GENOTYPING)
                        && !summInfo.getCode(CustomStrings.SUMMARIZED_INFO_TYPE).equals(CustomStrings.SUMMARIZED_INFO_BSEOS_TYPE)) {

                    PredefinedResult predRes = resultService.getPredefinedResult(summInfo, caseInfo);
                    row.put(CustomStrings.PARAM_CODE_BASE_TERM_COL, predRes.get(PredefinedResultHeader.GENOTYPING_BASE_TERM));

                } else if (!row.getCode(CustomStrings.TEST_AIM_COL).isEmpty())
                    PredefinedResultService.addParamAndResult(row, row.getCode(CustomStrings.TEST_AIM_COL));
            }
        }
    }
}
