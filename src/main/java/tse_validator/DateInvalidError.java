package tse_validator;

import java.util.Arrays;
import java.util.Collection;

import i18n_messages.TSEMessages;
import report_validator.ReportError;

public class DateInvalidError implements ReportError {

	private int sampDay;
	private int sampMonth;
	private int sampYear;

	private String date;

	public DateInvalidError(int sampDay, int sampMonth, int sampYear) {
		this.sampDay = sampDay;
		this.sampMonth = sampMonth;
		this.sampYear = sampYear;
		this.date = String.valueOf(this.sampDay) + "/" + String.valueOf(this.sampMonth) + "/" + String.valueOf(this.sampYear);
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
		return null;
	}

	@Override
	public String getSuggestions() {
		return TSEMessages.get("type.correct.values");
	}

	@Override
	public Collection<String> getErroneousValues() {
		return Arrays.asList(date);
	}

}
