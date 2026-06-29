package exception_manager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app_config.PropertiesReader;
import global_utils.Message;
import global_utils.Warnings;
import i18n_messages.TSEMessages;
import tse_main.StartUI;
import org.eclipse.swt.widgets.Shell;

public class ExceptionManager {

	private static final Logger LOGGER = LogManager.getLogger(StartUI.class);


	public Message manageThrowable(Throwable t) {

		Throwable root = t;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		LOGGER.fatal("Generic error occurred", root);
		
		if (root instanceof java.net.SocketTimeoutException) {
			return  this.createMessage("generic.socket.timeout");
		}

		if (root instanceof java.net.ConnectException) {
			return this.createMessage("generic.connection.error");
		}

		if (root instanceof javax.xml.soap.SOAPException) {
			 return this.createMessage("generic.soap.error");
		}

		if (root instanceof java.sql.SQLException) {
			 return this.createMessage("generic.sql.error");
		}

		if (root instanceof java.nio.file.AccessDeniedException) {
			return this.createMessage("generic.access.denied.error");
		}

		if (root instanceof java.io.FileNotFoundException) {
			return this.createMessage("generic.file.not.found.error");
		}

		if (root instanceof java.io.IOException) {
			return this.createMessage("generic.io.error");
		}

		if (root instanceof org.eclipse.swt.SWTException) {
			return this.createMessage("generic.swt.error");
		}

		if (root instanceof InvocationTargetException) {
			return this.createMessage("generic.invocation.target.error");
		}

		if (root instanceof SecurityException) {
			return this.createMessage("generic.security.error");
		}

		if (root instanceof IllegalArgumentException) {
			return this.createMessage("generic.illegal.error");
		}

		if (root instanceof OutOfMemoryError) {
			 return this.createMessage("generic.out.of.memory.error");
		}

		return this.createMessage("generic.error", PropertiesReader.getSupportEmail());
		
	}
	public Message manageException(Exception e) {
		Throwable t = e;
		return this.manageThrowable(t);
	}

	private Message createMessage(String property, String... values) {
		return Warnings.createFatal(TSEMessages.get(property, values));
	}
}
