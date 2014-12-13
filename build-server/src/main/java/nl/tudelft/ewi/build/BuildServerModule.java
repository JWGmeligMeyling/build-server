package nl.tudelft.ewi.build;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.spotify.docker.client.DockerDateFormat;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.docker.DockerManager;
import nl.tudelft.ewi.build.docker.DockerManagerImpl;
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
		bind(ObjectMapper.class).toProvider(
				new com.google.inject.Provider<ObjectMapper>() {
					@Override
					public ObjectMapper get() {
						ObjectMapper mapper = new ObjectMapper();
						mapper.registerModule(new MappingModule());
						mapper.registerModule(new GuavaModule());
						mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
						mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
						mapper.setDateFormat(new DockerDateFormat());
						return mapper;
					}
				});

		bind(DockerManager.class).to(DockerManagerImpl.class)
				.asEagerSingleton();

		findResourcesWith(Path.class);
		findResourcesWith(Provider.class);
	}

	private void findResourcesWith(Class<? extends Annotation> ann) {
		Reflections reflections = new Reflections(getClass().getPackage()
				.getName());
		for (Class<?> clasz : reflections.getTypesAnnotatedWith(ann)) {
			log.info("Registering resource {}", clasz);
			bind(clasz);
		}
	}

}
