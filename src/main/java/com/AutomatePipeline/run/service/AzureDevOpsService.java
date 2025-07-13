package com.AutomatePipeline.run.service;

import com.AutomatePipeline.run.service.PomServices.PomManipulations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AzureDevOpsService {

    private final PomManipulations pomManipulations;

    public AzureDevOpsService(PomManipulations pomManipulations) {
        this.pomManipulations = pomManipulations;
    }

    public static String PAT_FILE_PATH;
    public static String APPROVER_PAT_FILE_PATH;
    public static String MY_GUID;
    public HashMap<String, String> PR_BRANCH_MAP = new HashMap<>();

    /*public String triggerPipelineService(String organization, String project, int pipelineId) {

        //Creates a RestTemplate instance to be sent to the Azure DevOps API
        RestTemplate restTemplate = new RestTemplate();

        try {
            String PAT = Files.readString(Paths.get(PAT_FILE_PATH)).trim();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth("", PAT);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("definition", Map.of("id", pipelineId));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            String url = "https://dev.azure.com/" + organization + "/" + project + "/_apis/build/builds?api-version=7.1-preview.7";

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return "Pipeline "+pipelineId+ " triggered successfully!!";
            } else {
                return response.getStatusCode().toString();
            }

        } catch (Exception e) {
            return e.getMessage();
        }
    }*/

    /*public List<String> extractRepositoryNamesService(String jsonResponse, String targetProjectName) {
        List<String> repositoryNames = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode repositories = rootNode.path("value");

            for (JsonNode repo : repositories) {
                String projectName = repo.path("project").path("name").asText();
                if (targetProjectName.equals(projectName)) {
                    repositoryNames.add(repo.path("name").asText());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return repositoryNames;
    }*/

    public String updateBranchPomService(String organization, String project, String repositoryName, String sbtId) {


        String repoUrl = "https://dev.azure.com/" + organization + "/" + project + "/_git/" + repositoryName;
        String localRepoPath = "C:/Users/" + sbtId + "/AutomatePipeline/" + repositoryName;
        String pomFilePath = localRepoPath + "/pom.xml";

        String branchName = "bump-version-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        MY_GUID = "C:\\Users\\" + sbtId + "\\GUID.txt";

        Git git = null;
        try {
            PAT_FILE_PATH = "C:\\Users\\" + sbtId + "\\PAT.txt";
            // Read the Personal Access Token (PAT)
            String PAT = Files.readString(Paths.get(PAT_FILE_PATH)).trim();

            // Clone the repository and checkout master
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(localRepoPath))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("", PAT))
                    .call();

            git.checkout().setCreateBranch(true).setName(branchName).call();


            // Modify the pom.xml file in master
            String newRevisionVersion = pomManipulations.modifyPomFile(pomFilePath);

            // Commit the changes on master
            git.add().addFilepattern("pom.xml").call();
            git.commit().setMessage("Updated pom.xml on " + branchName + " to " + newRevisionVersion).call();


            // Push the changes to the remote feature branch
            git.push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(branchName + ":" + branchName)) // Specify local:remote refspec format
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("", PAT))
                    .call();


            // Call createPullRequest to create a PR for the branch
            String prUrl = createPullRequest(
                    organization,
                    project,
                    repositoryName,
                    branchName,
                    "master", // Target branch
                    newRevisionVersion,
                    PAT
            );

            // Extract the pull request ID from the URL
            String pullRequestId = prUrl.substring(prUrl.lastIndexOf("/") + 1);


            // Call patchPullRequest to enable auto-completion
            String userGuid = Files.readString(Paths.get(MY_GUID)).trim();

            patchPullRequest(organization, project, repositoryName, pullRequestId, PAT, userGuid);


            PR_BRANCH_MAP.put(prUrl, branchName);
            return prUrl;

        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
            return " Error: " + e.getMessage();
        } finally {
            if (git != null) {
                git.close(); // Ensures the Git repository is closed
            }
            File localRepo = new File(localRepoPath);
            if (localRepo.exists()) {
                int retryCount = 20; // Retry deletion up to 3 times
                boolean deleted = false;
                while (retryCount > 0 && !deleted) {
                    try {
                        FileUtils.deleteDirectory(localRepo);
                        System.out.println("Local repository deleted successfully: " + localRepoPath);
                        deleted = true;
                    } catch (IOException e) {
                        System.err.println("Failed to delete local repository: " + localRepoPath + " - " + e.getMessage());
                        e.printStackTrace();
                        retryCount--;
                        if (retryCount == 0) {
                            return "Error deleting local repository: " + e.getMessage();
                        }
                    }
                }
            } else {
                System.out.println("Local repository does not exist: " + localRepoPath);
            }
        }

    }

    public String createPullRequest(String organization, String project, String repositoryName, String sourceBranch, String targetBranch, String newRevisionVersion, String personalAccessToken) throws IOException {

        String userGuid = Files.readString(Paths.get(MY_GUID)).trim();
        System.out.println("User GUID: " + userGuid);

        String url = "https://dev.azure.com/" + organization + "/" + project + "/_apis/git/repositories/" + repositoryName + "/pullrequests?api-version=7.0";

        String auth = Base64.getEncoder().encodeToString((":" + personalAccessToken).getBytes());

        // Set title and description based on newRevisionVersion
        String titleAndDescription = "Updated pom.xml to " + newRevisionVersion;

        // Create the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sourceRefName", "refs/heads/" + sourceBranch);
        requestBody.put("targetRefName", "refs/heads/" + targetBranch);
        requestBody.put("title", titleAndDescription);
        requestBody.put("description", titleAndDescription);

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + auth);

        // Create the HTTP entity
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Make the POST request
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            // Parse the response body to extract the pullRequestId
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            String pullRequestId = responseBody.path("pullRequestId").asText();

            // Construct the full pull request URL
            String prUrl = "https://dev.azure.com/" + organization + "/" + project + "/_git/" + repositoryName + "/pullrequest/" + pullRequestId;
            System.out.println("Pull request created successfully: " + prUrl);


            return prUrl;
        } else {
            System.out.println("Failed to create pull request: " + response.getStatusCode());
            throw new IOException("Failed to create pull request: " + response.getStatusCode());
        }
    }

    public void patchPullRequest(String organization, String project, String repositoryName, String pullRequestId, String personalAccessToken, String userGuid) throws IOException {

        String url = "https://dev.azure.com/" + organization + "/" + project + "/_apis/git/repositories/" + repositoryName + "/pullrequests/" + pullRequestId + "?api-version=7.0";
        String auth = Base64.getEncoder().encodeToString((":" + personalAccessToken).getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("completionOptions", Map.of(
                "autoCompleteIgnoreConfigIds", new ArrayList<>(),
                "squashMerge", true,
                "deleteSourceBranch", true
        ));
        requestBody.put("autoCompleteSetBy", Map.of("id", userGuid));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + auth);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Create and configure RestTemplate inline
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println("Pull request updated successfully with auto-complete settings.");
        } else {
            System.out.println("Failed to update pull request: " + response.getStatusCode());
            throw new IOException("Failed to update pull request: " + response.getStatusCode());
        }
    }

    public List<String> approvePullRequestsByLinks(List<String> prLinks, String reviewerId, String sbtId) {
        List<String> results = new ArrayList<>();
        try {
            System.out.println("Reading PAT file...");

            APPROVER_PAT_FILE_PATH = "C:\\Users\\" + sbtId + "\\approver_PAT.txt";
            String PAT = Files.readString(Paths.get(APPROVER_PAT_FILE_PATH)).trim();
            System.out.println("PAT file read successfully.");

            for (String prLink : prLinks) {
                System.out.println("Processing PR link: " + prLink);
                try {
                    // Validate PR link format
                    String[] parts = prLink.split("/");
                    if (parts.length < 9) {
                        throw new IllegalArgumentException("Malformed PR link: " + prLink);
                    }

                    // Extract details from the PR link
                    System.out.println("Extracting details from PR link...");
                    String organization = parts[3];
                    String project = parts[4];
                    String repositoryName = parts[6];
                    String pullRequestId = parts[8];
                    System.out.println("Extracted details - Organization: " + organization + ", Project: " + project +
                            ", Repository: " + repositoryName + ", Pull Request ID: " + pullRequestId);

                    // Approve the PR
                    System.out.println("Approving PR...");
                    String result = approvePullRequest(organization, project, repositoryName, pullRequestId, reviewerId, PAT);
                    System.out.println(repositoryName + " PR approved:" + prLink);
                    results.add(repositoryName + " PR approved: " + prLink);
                } catch (Exception e) {
                    System.err.println("Failed to approve PR: " + prLink + " - " + e.getMessage());
                    results.add("Failed to approve PR: " + prLink + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading PAT file: " + e.getMessage());
            results.add("Error reading PAT file: " + e.getMessage());
        }
        return results;
    }

    private String approvePullRequest(String organization, String project, String repositoryName, String pullRequestId, String reviewerId, String personalAccessToken) throws IOException {

        // URL to update the status of a specific reviewer in the pull request
        String url = "https://dev.azure.com/" + organization + "/" + project + "/_apis/git/repositories/" + repositoryName + "/pullrequests/" + pullRequestId + "/reviewers/" + reviewerId + "?api-version=7.0";

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth("", personalAccessToken);

        // Create the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("vote", 10); // 10 indicates approval

        // Create the HTTP entity
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Make the PUT request to update the reviewer status
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return "PR approved successfully";
        } else {
            throw new IOException("Failed to approve PR: " + response.getStatusCode());
        }
    }


    public List<String> fetchBuildIdsByPullRequestDetails(String organization, String project) {
        List<String> buildIds = new ArrayList<>();
        try {
            // Read the Personal Access Token (PAT)
            String PAT = Files.readString(Paths.get(PAT_FILE_PATH)).trim();

            // Iterate over PR_BRANCH_MAP
            for (Map.Entry<String, String> entry : PR_BRANCH_MAP.entrySet()) {
                String prUrl = entry.getKey();
                String branchName = entry.getValue();

                // Extract pull request ID from the PR URL
                String[] parts = prUrl.split("/");
                if (parts.length < 9) {
                    buildIds.add("Malformed PR URL: " + prUrl);
                    continue;
                }
                String pullRequestId = parts[8];

                // Correctly use the repository name instead of the branch name
                String repositoryName = parts[6];

                // API URL to fetch pull request details
                String prDetailsUrl = String.format(
                        "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/pullRequests/%s?api-version=7.0",
                        organization, project, repositoryName, pullRequestId
                );

                // Set up headers with authentication
                HttpHeaders headers = new HttpHeaders();
                headers.setBasicAuth("", PAT);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> prResponse = restTemplate.exchange(prDetailsUrl, HttpMethod.GET, requestEntity, String.class);

                if (prResponse.getStatusCode().is2xxSuccessful()) {
                    // Parse the response to extract sourceVersion (commit ID)
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode prDetails = objectMapper.readTree(prResponse.getBody());
                    String sourceVersion = prDetails.path("sourceRefCommit").path("commitId").asText();

                    if (sourceVersion.isEmpty()) {
                        // Fallback to source branch if commit ID is missing
                        String sourceBranch = prDetails.path("sourceRefName").asText();
                        if (sourceBranch.isEmpty()) {
                            buildIds.add("Source branch not found for PR: " + prUrl);
                            System.out.println("API Response: " + prResponse.getBody()); // Log response for debugging
                            continue;
                        }

                        // Fetch the latest commit ID from the source branch
                        String branchCommitsUrl = String.format(
                                "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/commits?searchCriteria.itemVersion.version=%s&api-version=7.0",
                                organization, project, repositoryName, sourceBranch.replace("refs/heads/", "")
                        );

                        ResponseEntity<String> branchCommitsResponse = restTemplate.exchange(branchCommitsUrl, HttpMethod.GET, requestEntity, String.class);

                        if (branchCommitsResponse.getStatusCode().is2xxSuccessful()) {
                            JsonNode commits = objectMapper.readTree(branchCommitsResponse.getBody()).path("value");
                            if (commits.isArray() && commits.size() > 0) {
                                sourceVersion = commits.get(0).path("commitId").asText();
                            } else {
                                buildIds.add("PR: " + prUrl + " -> No commits found for branch: " + sourceBranch);
                                continue;
                            }
                        } else {
                            buildIds.add("PR: " + prUrl + " -> Failed to fetch commits for branch: " + sourceBranch + " - " + branchCommitsResponse.getStatusCode());
                            continue;
                        }
                    }

                    // API URL to fetch builds using sourceVersion
                    String buildsUrl = String.format(
                            "https://dev.azure.com/%s/%s/_apis/build/builds?sourceVersion=%s&api-version=7.1-preview.7",
                            organization, project, sourceVersion
                    );

                    ResponseEntity<String> buildsResponse = restTemplate.exchange(buildsUrl, HttpMethod.GET, requestEntity, String.class);

                    if (buildsResponse.getStatusCode().is2xxSuccessful()) {
                        JsonNode builds = objectMapper.readTree(buildsResponse.getBody()).path("value");
                        if (builds.isArray() && builds.size() > 0) {
                            for (JsonNode build : builds) {
                                buildIds.add("PR: " + prUrl + " -> Build ID: " + build.path("id").asText());
                            }
                        } else {
                            buildIds.add("PR: " + prUrl + " -> No builds found for commit: " + sourceVersion);
                        }
                    } else {
                        buildIds.add("PR: " + prUrl + " -> Failed to fetch builds for commit: " + sourceVersion + " - " + buildsResponse.getStatusCode());
                    }
                } else {
                    buildIds.add("PR: " + prUrl + " -> Failed to fetch pull request details - " + prResponse.getStatusCode());
                }
            }
        } catch (Exception e) {
            buildIds.add("Error: " + e.getMessage());
        } finally {
            PR_BRANCH_MAP.clear(); // Clear the HashMap after usage
        }
        return buildIds;
    }


}
