package tse_validator;

import java.util.Arrays;
import java.util.Collection;

import i18n_messages.TSEMessages;
import report_validator.ReportError;

public class SpecialCharacterFieldError implements ReportError {

	private String field;
	private String rowId;
	
	public SpecialCharacterFieldError(String field, String rowId) {
		this.field = field;
		this.rowId = rowId;
	}
	
	@Override
	public ErrorType getTypeOfError() {
		return ErrorType.ERROR;
	}

	@Override
	public String getErrorMessage() {
		return TSEMessages.get("special.character.found.message", field);
	}

	@Override
	public Collection<String> getInvolvedRowsIdsMessage() {
		return Arrays.asList(rowId);
	}

	@Override
	public String getSuggestions() {
		return TSEMessages.get("special.character.found.suggestion");
	}

	@Override
	public Collection<String> getErroneousValues() {
		return null;
	}


}
