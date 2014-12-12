package nl.tudelft.ewi.build.docker;

import java.io.IOException;


public interface DockerManager {
	
	void buildImage(String name, String dockerFileContents, ImageBuildObserver observer) throws IOException;

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
	 * @throws IOException 
	 */
	BuildReference run(Logger logger, DockerJob job) throws IOException;

	/**
	 * Terminates a running Docker container.
	 * 
	 * @param container
	 *            The id of the container to terminate.
	 * @throws IOException 
	 */
	void terminate(Identifiable container) throws IOException;

	/**
	 * @return The number of currently running containers according to the
	 *         Docker service.
	 * @throws IOException 
	 */
	int getActiveJobs() throws IOException;

}