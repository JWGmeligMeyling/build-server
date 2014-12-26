package nl.tudelft.ewi.build.jaxrs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.BadRequestException;

import lombok.extern.slf4j.Slf4j;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import nl.tudelft.ewi.build.Config;
import nl.tudelft.ewi.build.builds.BuildManager;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedBuildRequest;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedGitDirectoryPreparer;
import nl.tudelft.ewi.build.jaxrs.adapters.DevhubBuildInstruction;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult.Status;
import nl.tudelft.ewi.github.GithubHook;
import nl.tudelft.ewi.github.jaxrs.models.Head;
import nl.tudelft.ewi.github.jaxrs.models.PullRequestEvent;

@Slf4j
@Singleton
public class BuildGithubHook implements GithubHook {
	
	private final GitHub github;
	
	private final BuildManager manager;
	
	private final Config config;
	
	@Inject
	public BuildGithubHook(final GitHub github, final BuildManager manager,
			final Config config) {
		this.github = github;
		this.manager = manager;
		this.config = config;
	}
	
	@Override
	public void onPullRequest(PullRequestEvent event)
			throws InterruptedException, ExecutionException, IOException {

		log.info("Received pull request\n\t{}", event);
		String repoName = event.getRepository().getFullName();
		
		log.info("Myself:\n\t{}", github.getMyself());
		log.info("Fetching github repo {}", repoName);
		GHRepository githubRepo = github.getRepository(repoName);
		
		AdaptedBuildRequest request = new AdaptedBuildRequest();

		AdaptedGitDirectoryPreparer source = new AdaptedGitDirectoryPreparer();
		Head head = event.getPullRequest().getHead();
		source.setBranchName(head.getRef());
		source.setCommitId(head.getSha());
		source.setRepositoryUrl(head.getRepo().getCloneUrl());
	
		DevhubBuildInstruction instruction = new DevhubBuildInstruction();
		
		request.setSource(source);
		request.setInstruction(instruction);
		
		log.info("Setting build status to {} for {} at {}",
				GHCommitState.PENDING, repoName, head.getSha());
		githubRepo.createCommitStatus(head.getSha(), GHCommitState.PENDING,
				null, "Devhub Build Server build pending",
				"devhub-build-server");

		log.info("Scheduling build\n\t{}", request);
		BuildResult buildResult = manager.schedule(request).get();
		
		GHCommitState state = buildResult.getStatus() == Status.SUCCEEDED ?
				GHCommitState.SUCCESS : GHCommitState.FAILURE;

		log.info("Writing log to paste bin");
		String logUrl = writeLogToPastebin(head.getSha(),
				Joiner.on("\n").join(buildResult.getLogLines()));
		
		log.info("Setting build status to {} for {} at {}", state, repoName,
				head.getSha());
		
		githubRepo.createCommitStatus(head.getSha(), state, logUrl,
				"Devhub Build Server build success", "devhub-build-server");
	}
	
	private String writeLogToPastebin(final String uuid, final String contents) throws IOException {
	     URL url = new URL("http://pastebin.com/api/api_post.php");
	     
	     Map<String, String> arguments = Maps.newHashMap();
	     arguments.put("api_dev_key", config.getPasteBinToken());
	     arguments.put("api_paste_code", URLEncoder.encode(contents, "UTF-8"));
	     arguments.put("api_paste_private", "1");
	     arguments.put("api_paste_name", uuid);
	     arguments.put("api_option", "paste");
	     
	     StringBuilder a = new StringBuilder(contents.length());
	     
	     boolean separate = false;
         for (Map.Entry<String, String> e : arguments.entrySet()) {
        	 if(separate) a.append('&');
        	 a.append(e.getKey()).append('=').append(e.getValue());
             separate = true;
         }

         String text = a.toString();
	     
         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
         connection.setDoOutput(true);
         connection.setDoInput(true);
         connection.setInstanceFollowRedirects(false);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
         connection.setRequestProperty("charset", "utf-8");
         connection.setRequestProperty("Content-Length", "" + text.length());
         connection.setUseCaches(false);
         
         try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
        	 writer.write(text);
         }
         
         try(BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        	 String response = reader.readLine();
        	 if(response.startsWith("Bad API request,")) {
        		 log.info("Failed to store build log", new BadRequestException(response));
        		 return null;
        	 }
        	 return response;
         }
	}

}
