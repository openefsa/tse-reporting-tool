package tse_validator;

import java.util.Arrays;
import java.util.Collection;

import i18n_messages.TSEMessages;
import report_validator.ReportError;

public class MissingGenotypingForScrapieType implements ReportError {

	private final String rowId;

	public MissingGenotypingForScrapieType(String rowId) {
		this.rowId = rowId;
	}
	@Override
	public ErrorType getTypeOfError() {
		return ErrorType.ERROR;
	}

	@Override
	public String getErrorMessage() {
		return TSEMessages.get("genotyping.mandatory.for.scrapie");
	}

	@Override
	public Collection<String> getInvolvedRowsIdsMessage() {
		return Arrays.asList(rowId);
	}

	@Override
	public String getSuggestions() {
		return TSEMessages.get("add.genotyping.scrapie");
	}

	@Override
	public Collection<String> getErroneousValues() {
		return null;
	}

}
