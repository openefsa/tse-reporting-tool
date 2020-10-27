<p align="center">
	<img src="http://www.efsa.europa.eu/profiles/efsa/themes/responsive_efsa/logo.png" alt="European Food Safety Authority"/>
</p>

# Transmissible spongiform encephalopathies tool
The TSE data reporting tool is an open source Java client tool developed for the members of the Scientific Network for Zoonoses monitoring. The tool allows countries to submit and edit their data and automatically upload them into the EFSA Data Collection Framework (DCF) as XML data files.

<p align="center">
    <img src="src/main/resources/icons/TSE_Splash.bmp" alt="TSE icon"/>
</p>

## Dependencies
All project dependencies are listed in the [pom.xml](https://github.com/openefsa/tse-reporting-tool/blob/master/pom.xml) file.

## Import the project
In order to import the project correctly into the integrated development environment (e.g. Eclipse), it is necessary to download the TSE together with all its dependencies.
The TSE and all its dependencies are based on the concept of "project object model" and hence Apache Maven is used for the specific purpose.
In order to correctly import the project into the IDE it is firstly required to download or clone all the required dependencies as stated in the list below:

	<dependencies>
		<!-- tse project dependencies -->
		<module>tse-data-reporting-tool</module>
		<module>efsa-rcl</module>
		<module>email-generator</module>
		<module>dcf-webservice-framework</module>
		<module>exceptions-manager</module>
		<module>http-manager</module>
		<module>http-manager-gui</module>
		<module>progress-bar</module>
		<module>sql-script-executor</module>
		<module>version-manager</module>
		<module>window-size-save-restore</module>
		<module>zip-manager</module>
	</dependencies>
	
Next, extract all the zip packages inside your workspace. At this stage you can simply open the IDE and import all projects available in the workspace.

_Please note that Maven is required in order to download the libraries required by the TSE tool._

_Please note that the "SWT (swt_3.7.1.v3738a.jar)" and the "Jface (org.eclipse.jface_3.7.0.I20110522-1430.jar)" libraries must be downloaded and installed manually in the Maven local repository since are custom versions used in the tool ((install 3rd party jars)[https://maven.apache.org/guides/mini/guide-3rd-party-jars-local.html])._

### Notes for developers
Please note that the "compact", "config" and "picklists" folders are required by the tool and therefore errors occur if missing.

