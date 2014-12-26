package nl.tudelft.ewi.build;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PropertyBasedConfig implements Config {

	private final Properties properties;
	
	public PropertyBasedConfig() {
		this.properties = new Properties();
		reload();
	}
	
	public void reload() {
		try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/config.properties"))) {
			properties.load(reader);
		}
		catch (IOException e) {
			log.error(e.getMessage());
		}
	}
	
	public int getHttpPort() {
		return Integer.parseInt(properties.getProperty("http.port", "8080"));
	}
	
	public int getMaximumConcurrentJobs() {
		return Integer.parseInt(properties.getProperty("docker.max-containers"));
	}
	
	public String getDockerHost() {
		return properties.getProperty("docker.host", "https://192.168.59.103:2376");
	}
	
	public String getStagingDirectory() {
		return properties.getProperty("docker.staging-directory");
	}
	
	public String getWorkingDirectory() {
		return properties.getProperty("docker.working-directory");
	}
	
	public String getClientId() {
		return properties.getProperty("authorization.client-id");
	}
	
	public String getClientSecret() {
		return properties.getProperty("authorization.client-secret");
	}

	@Override
	public String getCertificateDir() {
		return properties.getProperty("docker.cert-directory");
	}

	@Override
	public String getGitOAuthToken() {
		return System.getenv("github.client.oauth-token");
	}

	@Override
	public String getPasteBinToken() {
		return System.getenv("pastebin.oauth-token");
	}

}
