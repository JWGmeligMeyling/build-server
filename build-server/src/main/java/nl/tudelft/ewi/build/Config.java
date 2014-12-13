package nl.tudelft.ewi.build;

import java.io.File;


public interface Config {

	int getHttpPort();
	
	int getMaximumConcurrentJobs();
	
	String getDockerHost();
	
	File getCertificateDirectory();
	
	String getStagingDirectory();
	
	String getWorkingDirectory();
	
	String getClientId();
	
	String getClientSecret();
	
}
