package tse_options;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.xml.sax.SAXException;

import amend_manager.AmendException;
import app_config.PropertiesReader;
import dataset.RCLDatasetStatus;
import global_utils.Message;
import global_utils.Warnings;
import i18n_messages.TSEMessages;
import message.MessageConfigBuilder;
import message.SendMessageException;
import message_creator.OperationType;
import providers.ITableDaoService;
import providers.TseReportService;
import report.ReportException;
import soap.DetailedSOAPException;
import table_skeleton.TableVersion;
import tse_config.CustomStrings;
import tse_config.TSEWarnings;
import tse_report.TseReport;
import table_relations.Relation;

public class ActionsTestConnection {
	private static final Logger LOGGER = LogManager.getLogger(ActionsTestConnection.class);
	
	public static TseReport createTestReport() throws IOException {
		
		TseReport report = new TseReport();
		report.setCountry("TEST");
		report.setSenderId("TEST");
		report.setStatus(RCLDatasetStatus.DRAFT);
		report.setMonth("1");
		report.setYear("2010");
		report.setVersion(TableVersion.getFirstVersion());
		report.setMessageId("TEST");
		report.setId("");  // empty
		report.setLastMessageId("TEST");
		report.setLastModifyingMessageId("TEST");
		report.setLastValidationMessageId("TEST");
		
		Relation.injectGlobalParent(report, CustomStrings.PREFERENCES_SHEET);
		
		return report;
	}
	
	
	public static Message manageTestConnectionWithReport(TseReportService reportService, ITableDaoService daoService) {
		Message msg = null;
		TseReport report = null;
		try {
			
			report = createTestReport();
			
			// save report in db in order to perform send
			daoService.add(report);
			
			MessageConfigBuilder config = reportService.getSendMessageConfiguration(report);
			config.setOpType(OperationType.TEST);
			
			reportService.exportAndSend(report, config);

			// here is success
			String title = TSEMessages.get("success.title");
			String message = TSEMessages.get("test.connection.success");
			int style = SWT.ICON_INFORMATION;
			
			msg = Warnings.create(title, message, style);
			
			
		} catch (DetailedSOAPException e) {
			LOGGER.error("Test connection failed", e);
			e.printStackTrace();

			msg = Warnings.createSOAPWarning(e);
		} catch (ParserConfigurationException | SAXException | IOException e) {			
			LOGGER.error("Test connection failed", e);
			e.printStackTrace();
			
			msg = Warnings.createFatal(TSEMessages.get("test.connection.fail3", 
					PropertiesReader.getSupportEmail()), report);
			
		} catch (SendMessageException e) {
			LOGGER.error("Test connection failed", e);
			// here we got TRXKO
			e.printStackTrace();
			
			msg = TSEWarnings.getSendMessageWarning(e, report);
			
		} catch (ReportException e) {
			LOGGER.error("Test connection failed", e);
			// There an invalid operation was used
			e.printStackTrace();
			
			msg = Warnings.createFatal(TSEMessages.get("test.connection.fail2",
					PropertiesReader.getSupportEmail()), report);
		} catch (AmendException e) {
			LOGGER.error("This should never happen (amendments are not processed in the test connection)", e);
			e.printStackTrace();
		}
		finally {
			
			
			// delete the report from the db
			if (report != null)
				report.delete();
		}
		return msg;
	}
}