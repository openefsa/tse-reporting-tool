package tse_validator;

import java.util.Collection;
import java.util.Collections;

import i18n_messages.TSEMessages;
import report_validator.ReportError;

public class DateInvalidError implements ReportError {

	private final String date;
	private final String invalidRow;

	public DateInvalidError(String invalidRow, int sampDay, int sampMonth, int sampYear) {
		this.invalidRow = invalidRow;
		this.date = String.format("%s/%s/%s", sampDay, sampMonth, sampYear);
	}

	@Override
	public ErrorType getTypeOfError() {
		return ErrorType.ERROR;
	}

	@Override
	public String getErrorMessage() {
		return TSEMessages.get("invalid.date");
	}

	@Override
	public Collection<String> getInvolvedRowsIdsMessage() {
		return Collections.singletonList(invalidRow);
	}

	@Override
	public String getSuggestions() {
		return TSEMessages.get("type.correct.values");
	}

	@Override
	public Collection<String> getErroneousValues() {
		return Collections.singletonList(date);
	}

}
