package nl.tudelft.ewi.build;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.builds.BuildManager;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptersMappingModule;
import nl.tudelft.ewi.build.jaxrs.json.MappingModule;

import org.jboss.resteasy.plugins.guice.ext.JaxrsModule;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;
import org.reflections.Reflections;

@Slf4j
public class BuildServerModule extends AbstractModule {
	
	private final Config config;

	public BuildServerModule(Config config) {
		this.config = config;
	}

	@Override
	protected void configure() {
		install(new RequestScopeModule());
		install(new JaxrsModule());
		
		bind(Config.class).toInstance(config);
		bind(ObjectMapper.class).toProvider(new com.google.inject.Provider<ObjectMapper>() {
			@Override
			public ObjectMapper get() {
				ObjectMapper mapper = new ObjectMapper();
				mapper.registerModule(new MappingModule());
				mapper.registerModule(new AdaptersMappingModule());
				return mapper;
			}
		});
		
		bind(ExecutorService.class).toInstance(Executors.newCachedThreadPool());
		
		try {
			bind(DockerClient.class).toInstance(DefaultDockerClient.builder()
				.uri(config.getDockerHost())
				.dockerCertificates(new DockerCertificates(new File(config.getCertificateDir()).toPath()))
				.build());
		}
		catch (DockerCertificateException t) {
			throw new RuntimeException(t);
		}
		
		bind(BuildManager.class).asEagerSingleton();
		
		findResourcesWith(Path.class);
		findResourcesWith(Provider.class);
	}

	private void findResourcesWith(Class<? extends Annotation> ann) {
		Reflections reflections = new Reflections(getClass().getPackage().getName());
		for (Class<?> clasz : reflections.getTypesAnnotatedWith(ann)) {
			log.info("Registering resource {}", clasz);
			bind(clasz);
		}
	}

}
