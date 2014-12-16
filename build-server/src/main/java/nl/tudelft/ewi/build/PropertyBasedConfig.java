package nl.tudelft.ewi.build;

import java.io.File;
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

	@Override
	public int getHttpPort() {
		return Integer.parseInt(properties.getProperty("http.port", "8080"));
	}

	@Override
	public int getMaximumConcurrentJobs() {
		return Integer.parseInt(properties.getProperty("docker.max-containers"));
	}

	@Override
	public String getDockerHost() {
		return properties.getProperty("docker.host", "http://localhost:4243");
	}

	@Override
	public String getStagingDirectory() {
		return properties.getProperty("docker.staging-directory");
	}

	@Override
	public String getWorkingDirectory() {
		return properties.getProperty("docker.working-directory");
	}

	@Override
	public String getClientId() {
		return properties.getProperty("authorization.client-id");
	}

	@Override
	public String getClientSecret() {
		return properties.getProperty("authorization.client-secret");
	}

	@Override
	public File getCertificateDirectory() {
		return new File(properties.getProperty("docker.cert-directory"));
	}
	
}
