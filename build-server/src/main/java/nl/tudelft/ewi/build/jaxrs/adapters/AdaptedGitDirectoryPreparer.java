package nl.tudelft.ewi.build.jaxrs.adapters;

import java.io.File;
import java.io.PrintWriter;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Slf4j
@EqualsAndHashCode(callSuper=false)
@JsonIgnoreProperties(ignoreUnknown=true)
public class AdaptedGitDirectoryPreparer extends AdaptedDirectoryPreparer {
	
	private String repositoryUrl;
	
	private String branchName;
	
	private String commitId;

	@Override
	public void prepareStagingDirectory(File stagingDirectory, PrintWriter writer)
			throws DirectoryPreparerException {
		Git git = cloneRepository(stagingDirectory, writer);
		checkoutCommit(git, writer);
	}

	private Git cloneRepository(File stagingDirectory, PrintWriter writer) throws DirectoryPreparerException {
		try {
			log.info("Cloning from repository: {}", repositoryUrl);
			CloneCommand clone = Git.cloneRepository();
			clone.setBare(false);
			clone.setDirectory(stagingDirectory);
            clone.setCloneAllBranches(true);
            clone.setURI(repositoryUrl);
            return clone.call();
		}
		catch (GitAPIException e) {
			writer.println("[FATAL] Failed to clone from repository: " + repositoryUrl);
			throw new DirectoryPreparerException(e);
		}
	}

	private void checkoutCommit(Git git, PrintWriter writer) throws DirectoryPreparerException {
		try {
			String ref = branchName;
			if(ref == null) ref = "HEAD";
			
			log.info("Checking out revision: {}", commitId);
            CheckoutCommand checkout = git.checkout();
            checkout.setStartPoint(commitId);
            checkout.setName(ref);
            checkout.call();
		}
		catch (GitAPIException e) {
			writer.println("[FATAL] Failed to checkout to specified commit: " + commitId);
			throw new DirectoryPreparerException(e);
		}
	}
	
}
