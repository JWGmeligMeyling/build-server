package nl.tudelft.ewi.build.docker;

import javax.inject.Inject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.Config;

import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarOutputStream;

@Slf4j
public class DockerManagerImpl implements DockerManager {

	private final ListeningExecutorService executor;
	private final DockerClientConfig dockerClientConfig;

	@Inject
	public DockerManagerImpl(Config config) {
		int poolSize = config.getMaximumConcurrentJobs() * 2;
		this.executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(poolSize));
		
		this.dockerClientConfig = DockerClientConfig.createDefaultConfigBuilder()
				.withUri(config.getDockerHost())
				.withDockerCertPath("/Users/jgmeligmeyling/.boot2docker/certs/boot2docker-vm/")
				.build();
	}
	
	@Override
	public BuildReference run(final Logger logger, final DockerJob job) throws IOException {
		final Identifiable containerId = create(job);

		final Future<?> future = executor.submit(new Runnable() {
			@Override
			public void run() {
				
				try {
					start(containerId, job);
					logger.onStart();

					Future<?> logFuture = fetchLog(containerId, logger);
					StatusCode code = awaitTermination(containerId);
					logFuture.cancel(true);

					stopAndDelete(containerId);
					logger.onClose(code.getStatusCode());
				}
				catch (IOException e) {
					log.warn(e.getMessage(), e);
				}
			}
		});
		
		return new BuildReference(job, containerId, new Future<Object>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				if (future.cancel(mayInterruptIfRunning)) {
					log.warn("Terminating container: {} because it was cancelled.", containerId);
					
					try {
						stopAndDelete(containerId);
						
						log.warn("Container: {} was terminated forcefully.", containerId);
						return true;
					}
					catch (IOException e) {
						log.warn("Container could not be stopped", e);
					}
					
				}
				return false;
			}

			@Override
			public boolean isCancelled() {
				return future.isCancelled();
			}

			@Override
			public boolean isDone() {
				return future.isDone();
			}

			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return future.get();
			}

			@Override
			public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				return future.get(timeout, unit);
			}
		});
	}
	
	@Override
	public void terminate(Identifiable container) throws IOException {
		stopAndDelete(container);
	}

	@Override
	public int getActiveJobs() throws IOException {
		int counter = 0;
		for (Container container : getContainers().values()) {
			if (!Strings.emptyToNull(container.getStatus()).startsWith("Exit ")) {
				return counter++;
			}
		}
		return counter;
	}

	@Override
	public void buildImage(String name, String dockerFileContents, ImageBuildObserver observer) throws IOException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(dockerFileContents));
		
		name = URLEncoder.encode(name, "UTF-8");
		
		File tempDir = Files.createTempDir();
		File archive = new File(tempDir, "image.tar");
		File dockerFile = new File(tempDir, "Dockerfile");
		
		FileWriter writer = new FileWriter(dockerFile);
		writer.write(dockerFileContents);
		writer.flush();
		writer.close();
		
		try (TarOutputStream out = new TarOutputStream(new BufferedOutputStream(new FileOutputStream(archive)))) {
			out.putNextEntry(new TarEntry(dockerFile, dockerFile.getName()));
			
			int count;
			byte data[] = new byte[2048];
			try (BufferedInputStream origin = new BufferedInputStream(new FileInputStream(dockerFile))) {
				while((count = origin.read(data)) != -1) {
					out.write(data, 0, count);
				}
			}
			out.flush();
		}

		DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build();
		
		BuildImageCmd command = docker
				.buildImageCmd(archive)
				.withTag(name)
				.withNoCache()
				.withRemove(true);
		
		ObjectMapper mapper = new ObjectMapper();
		
		try(InputStream output = command.exec();
			BufferedReader reader = new BufferedReader(new InputStreamReader(output))) {
			
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("{\"stream\":")) {
					observer.onMessage(mapper.readValue(line, Stream.class).getStream());
				}
				else if (line.startsWith("{\"error\":")) {
					observer.onError(mapper.readValue(line, Error.class).getErrorDetail().getMessage());
				}
			}
			
		}
		finally {
			observer.onCompleted();
			docker.close();
			archive.delete();
			dockerFile.delete();
			tempDir.delete();
		}
	}
	
	private Map<Identifiable, Container> getContainers() throws IOException {
		try (DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build()) {
			log.debug("Listing containers...");
			List<Container> containers = Lists.newArrayList();
			
			for(com.github.dockerjava.api.model.Container container :  docker.listContainersCmd().exec()) {
				containers.add(new Container()
					.setCmd(Lists.newArrayList(container.getCommand()))
					.setId(container.getId())
					.setImage(container.getImage())
					.setStatus(container.getStatus())
				);
			}
			
			Map<Identifiable, Container> mapping = Maps.newLinkedHashMap();
			for (Container container : containers) {
				Identifiable identifiable = new Identifiable();
				identifiable.setId(container.getId());
				mapping.put(identifiable, container);
			}
			return mapping;
		}
	}
	
	private Identifiable create(final DockerJob job) throws IOException {
		List<Volume> volumes = Lists.newArrayList();
		if (job.getMounts() != null) {
			for (String mount : job.getMounts()
				.values()) {
				volumes.add(new Volume(mount));
			}
		}
		
		List<String> commands = CommandParser.parse(job.getCommand());

		try (DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build()) {
			final CreateContainerResponse response = docker.createContainerCmd(job.getImage())
				.withTty(true)
				.withWorkingDir(job.getWorkingDirectory())
				.withVolumes(volumes.toArray(new Volume[volumes.size()]))
				.withCmd(commands.toArray(new String[commands.size()])).exec();
			
			final Identifiable result = new Identifiable();
			result.setWarnings(response.getWarnings());
			result.setId(response.getId());
			return result;
		}
	}

	private void start(final Identifiable container, final DockerJob job) throws IOException {
		List<Bind> binds = Lists.newArrayList();
		if (job.getMounts() != null) {
			for (Entry<String, String> mount : job.getMounts()
				.entrySet()) {
				binds.add(new Bind(mount.getKey(), new Volume(mount.getValue())));
			}
		}
		
		try (DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build()) {
			docker.startContainerCmd(container.getId())
				.withBinds(binds.toArray(new Bind[binds.size()]))
				.withLxcConf(new LxcConf("lxc.utsname", "docker"))
				.exec();
		}
	}

	private Future<?> fetchLog(final Identifiable container, final Logger collector) {
		return executor.submit(new Runnable() {
			@Override
			public void run() {
				
				try (final DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build();
						
					final InputStream logs = docker
							.attachContainerCmd(container.getId())
							.withLogs(true)
							.withFollowStream()
							.withStdErr()
							.withStdOut().exec();
						
					final InputStreamReader reader = new InputStreamReader(logs)) {
					
					boolean finished = false;
					StringBuilder builder = new StringBuilder();
					while (!finished) {
						int i = reader.read();
						if (i == -1) {
							collector.onNextLine(builder.toString());
							break;
						}

						char c = (char) i;
						if (c == '\n' || finished) {
							collector.onNextLine(builder.toString());
							builder.delete(0, builder.length() + 1);
						}
						else if (c != '\r') {
							builder.append(c);
						}
					}
						
				}
				catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		});
	}

	private StatusCode awaitTermination(final Identifiable container) throws IOException {
		log.debug("Awaiting termination of container: {}", container.getId());

		StatusCode status;
		while (true) {
			status = getStatus(container);
			Integer statusCode = status.getStatusCode();
			if (statusCode != null) {
				break;
			}

			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				log.error(e.getMessage(), e);
			}
		}

		log.debug("Container: {} terminated with status: {}", container.getId(), status);
		return status;
	}
	
	private boolean isStopped(final Identifiable identifiable) throws IOException {
		Map<Identifiable, Container> containers = getContainers();
		if (containers.containsKey(identifiable)) {
			Container container = containers.get(identifiable);
			return Strings.nullToEmpty(container.getStatus()).startsWith("Exit ");
		}
		return true;
	}

	private StatusCode getStatus(final Identifiable container) throws IOException {
		try (DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build()) {
			Integer result = docker.waitContainerCmd(container.getId()).exec();
			StatusCode status = new StatusCode();
			status.setStatusCode(result);
			return status;
		}
	}
	
	private boolean exists(Identifiable container) throws IOException {
		return getContainers().containsKey(container);
	}
	
	private void stopAndDelete(Identifiable container) throws IOException {
		log.debug("Attempting to stop container: {}", container);
		do {
			stop(container);
			waitFor(1000);
		}
		while (!isStopped(container));

		log.debug("Attempting to delete container: {}", container);
		do {
			delete(container);
			waitFor(1000);
		}
		while (exists(container));
	}

	private void stop(final Identifiable container) throws IOException {
		try (DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build()) {
			log.debug("Stopping container: {}", container.getId());
			docker.stopContainerCmd(container.getId()).withTimeout(5);
		}
	}

	private void delete(final Identifiable container) throws IOException {
		try (DockerClient docker = DockerClientBuilder.getInstance(dockerClientConfig).build()) {
			docker.removeContainerCmd(container.getId())
				.withForce()
				.withRemoveVolumes(true)
				.exec();
		}
	}
	
	private void waitFor(int millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			log.warn(e.getMessage(), e);
		}
	}
	
}
