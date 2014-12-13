import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerException;

import nl.tudelft.ewi.build.Config;
import nl.tudelft.ewi.build.PropertyBasedConfig;
import nl.tudelft.ewi.build.docker.DockerManager;
import nl.tudelft.ewi.build.docker.DockerManagerImpl;
import nl.tudelft.ewi.build.docker.ImageBuildObserver;


public class JavaMaven {

	public static void main(String[] args) throws IOException, DockerException, InterruptedException, DockerCertificateException {
		Config config = new PropertyBasedConfig();
		DockerManager manager = new DockerManagerImpl(config);
		System.out.println("Building image...");
		String contents = IOUtils.toString(JavaMaven.class.getResourceAsStream("/dockerfiles/java-maven/Dockerfile"));
		
		manager.buildImage("java-maven", contents, new ImageBuildObserver() {
			
			@Override
			public void onMessage(String message) {
				System.out.print(message);
			}
			
			@Override
			public void onError(String error) {
				System.err.print(error);
			}
			
			@Override
			public void onCompleted() {
				System.out.println("Completed");
			}
		});
	}

}
