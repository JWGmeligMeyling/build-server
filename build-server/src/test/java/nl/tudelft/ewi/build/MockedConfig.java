package nl.tudelft.ewi.build;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain=true)
public class MockedConfig implements Config {

	private int httpPort = 8082;
	
	private int maximumConcurrentJobs = 3;

	private String dockerHost;
	
	private String certificateDir;

	private String stagingDirectory;
	
	private String workingDirectory;

	private String clientId;

	private String clientSecret;

	private String gitOAuthToken;
	
	private String pasteBinToken;

}
