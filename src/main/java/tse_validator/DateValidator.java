package tse_validator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DateValidator {

	private static final Logger LOGGER = LogManager.getLogger(DateValidator.class);


	public boolean validate(int sampDay, int sampMonth, int sampYear)  {

		boolean checkForLeapYear = checkForLeapYear(sampDay, sampMonth, sampYear);
		boolean checkMonths = checkMonths(sampDay, sampMonth, sampYear);

		return checkForLeapYear && checkMonths;
	}

	public boolean checkForLeapYear(int sampDay, int sampMonth, int sampYear)  {

		if (sampDay == 29 && sampMonth == 2) {

			boolean isLeapYear = ((sampYear % 4 == 0) && (sampYear % 100 != 0) || (sampYear % 400 == 0));

	        if (isLeapYear) {
	        	LOGGER.info(sampYear + " is a leap year.");
	            return true;
	        } else {
	        	LOGGER.info(sampYear + " is not a leap year.");
	        	return false;
	        }
		} 
		return true;
	}

	public boolean checkMonths(int sampDay, int sampMonth, int sampYear)  {

		 if (sampMonth == 4 || sampMonth == 6 || sampMonth == 9 || sampMonth == 11) {
			 if (sampDay <= 30) {
				 return true;
			 } else {
				 return false;
			 }
		 } else if (sampMonth == 2) {
			 if (sampDay <= 29) {
				 return true;
			 } else {
				 return false;
			 } 
		 } else {
			 if (sampDay <= 31) {
				 return true;
			 } else {
				 return false;
			 }
		 } 
	}

}
