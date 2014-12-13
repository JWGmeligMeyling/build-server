package nl.tudelft.ewi.build.docker;

import java.io.IOException;

import com.spotify.docker.client.DockerException;


public interface DockerManager {
	
	void buildImage(String name, String dockerFileContents, ImageBuildObserver observer) throws IOException, DockerException, InterruptedException;

	/**
	 * Starts a new container in Docker and attaches the specified
	 * {@link Logger}.
	 * 
	 * @param logger
	 *            The {@link Logger} to attach. This object will store logs
	 *            caught while listening and the exit code upon the container's
	 *            termination.
	 * @param job
	 *            The {@link DockerJob} describing the container setup and the
	 *            sort of job to run inside the container.
	 * @return A {@link BuildReference} which allows the requester to terminate
	 *         the container or retrieve information about the container.
	 * @throws InterruptedException 
	 * @throws DockerException 
	 */
	BuildReference run(Logger logger, DockerJob job) throws DockerException, InterruptedException;

	/**
	 * Terminates a running Docker container.
	 * 
	 * @param container
	 *            The id of the container to terminate.
	 * @throws InterruptedException 
	 * @throws DockerException 
	 */
	void terminate(Identifiable container) throws DockerException, InterruptedException;

	/**
	 * @return The number of currently running containers according to the
	 *         Docker service.
	 * @throws InterruptedException 
	 * @throws DockerException 
	 */
	int getActiveJobs() throws DockerException, InterruptedException;

}