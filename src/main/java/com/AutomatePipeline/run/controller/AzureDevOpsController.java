package com.AutomatePipeline.run.controller;


import com.AutomatePipeline.run.service.AzureDevOpsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api/devops")
public class AzureDevOpsController {

    private static final String AZURE_DEVOPS_URL = "https://dev.azure.com/organization";
    private static final String PAT_FILE_PATH = "C:\\Users\\pat.txt";
    private static final String ORGANIZATION = "organization";
    private static final String PROJECT = "1034";


    private final AzureDevOpsService azureDevOpsService;

    public static String APPROVER_GUID_PATH;

    public AzureDevOpsController(AzureDevOpsService azureDevOpsService) {
        this.azureDevOpsService = azureDevOpsService;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> pingAzureDevops() {

        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(AZURE_DEVOPS_URL, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok("Successful ping!!");
            } else {
                return ResponseEntity.status(response.getStatusCode()).body(response.getStatusCode().toString());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

    }

    @Value("${repo.names}")
    private String repoNames;

    @GetMapping("/api/devops/repositories")
    public ResponseEntity<List<String>> getRepositoryNames() {
        List<String> repositories = Arrays.asList(repoNames.split(","));
        return ResponseEntity.ok(repositories);
    }

    /*@PostMapping("/trigger-multiple-builds") //Just trigger multiple builds of master pipelines
    public ResponseEntity<List<String>> triggerBuilds(@RequestParam String organization,
                                                @RequestParam String project,
                                                @RequestParam List<Integer> pipelineIds) {

        ExecutorService executorService = Executors.newFixedThreadPool(pipelineIds.size());
        List<Future<String>> futures = new ArrayList<>();

        for(int pipelineId : pipelineIds){
            futures.add(executorService.submit(() -> azureDevOpsService.triggerPipelineService(organization, project, pipelineId)));
        }

        List<String> results = new ArrayList<>();
        for(Future<String> future : futures){
            try {
                results.add(future.get());
            } catch (Exception e) {
                results.add("Error: " + e.getMessage());
            }
        }

        executorService.shutdown();
        return ResponseEntity.ok(results);
    }*/

    /*@PostMapping("/get-repo-details")
    public String getRepositoryDetails(@RequestParam String organization, @RequestParam String project) {
        RestTemplate restTemplate = new RestTemplate();

        try {
            // Read the Personal Access Token (PAT)
            String PAT = Files.readString(Paths.get(PAT_FILE_PATH)).trim();

            // Set up headers with authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth("", PAT);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // API URL to fetch repositories
            String url = "https://dev.azure.com/" + organization + "/" + project + "/_apis/git/repositories?api-version=7.1-preview.1";

            // Make the API call
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return azureDevOpsService.extractRepositoryNamesService(response.getBody(), project).toString(); // Return repository details
            } else {
                return "Failed to fetch repository details: " + response.getStatusCode();
            }

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }*/

    @PostMapping("/update-branch-pom") //Updates pom.xml version to next version for a single repo
    public ResponseEntity<String> updateBranchPom(@RequestParam String organization,
                                                  @RequestParam String project,
                                                  @RequestParam String repositoryName,
                                                  @RequestParam String sbtId) {
        try {
            String result = azureDevOpsService.updateBranchPomService(organization, project, repositoryName, sbtId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/multiple-repo-update")
    public ResponseEntity<String> updateMultipleRepos(@RequestParam String organization,
                                                      @RequestParam String project,
                                                      @RequestParam List<String> repositoryNames,
                                                      @RequestParam String sbtId) {
        ExecutorService executorService = Executors.newFixedThreadPool(repositoryNames.size());
        List<Future<String>> futures = new ArrayList<>();
        List<String> results = new ArrayList<>();

        try {
            for (String repositoryName : repositoryNames) {
                futures.add(executorService.submit(() -> azureDevOpsService.updateBranchPomService(organization, project, repositoryName, sbtId)));
            }

            for (Future<String> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    results.add("Error: " + e.getMessage());
                }
            }

            executorService.shutdown();
            APPROVER_GUID_PATH = "C:\\Users\\" + sbtId + "\\approver_GUID.txt";
            String approverGUID = Files.readString(Paths.get(APPROVER_GUID_PATH)).trim();
            ;
            List<String> resultsForApproval = azureDevOpsService.approvePullRequestsByLinks(results, approverGUID, sbtId);

            /*List<String> buildIDs = azureDevOpsService.fetchBuildIdsByPullRequestDetails(organization, project);
            System.out.println("Build IDs: " + buildIDs);*/

            return ResponseEntity.ok(resultsForApproval.toString());
        } catch (Exception e) {
            executorService.shutdown();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to update multiple repositories: " + e.getMessage()).toString());
        }
    }

//    @PostMapping("all-repo-update")
//    public ResponseEntity<String> updateAllRepos(@RequestParam String organization,
//                                                 @RequestParam String project,
//                                                 @RequestParam String sbtId) {
//
//        ExecutorService executorService = Executors.newFixedThreadPool(allRepoNames.size());
//        List<Future<String>> futures = new ArrayList<>();
//
//        for (String repositoryName : allRepoNames) {
//            futures.add(executorService.submit(() -> azureDevOpsService.updateBranchPomService(organization, project, repositoryName, sbtId)));
//        }
//
//        StringBuilder results = new StringBuilder();
//        for (Future<String> future : futures) {
//            try {
//                results.append(future.get()).append(", ");
//            } catch (Exception e) {
//                results.append("Error: ").append(e.getMessage()).append(", ");
//            }
//        }

//        executorService.shutdown();
//        return ResponseEntity.ok(results.toString());
//
//    }


    /*@PostMapping("/approve-pr-links")
    public ResponseEntity<List<String>> approvePRsByLinks(@RequestParam List<String> prLinks, String reviewerId) {
        try {
            List<String> results = azureDevOpsService.approvePullRequestsByLinks(prLinks, reviewerId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonList("Error: " + e.getMessage()));
        }

    }*/

    @GetMapping("/pullrequest/{pullRequestId}")
    public ResponseEntity<String> getPullRequestDetails(@PathVariable String pullRequestId) throws IOException {
        String organization = ORGANIZATION;
        String project = PROJECT;
        String repositoryId = "COB-cob-web";
        String accessToken = Files.readString(Paths.get(PAT_FILE_PATH)).trim();

        String url = String.format(
                "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/pullRequests/%s?api-version=6.0",
                organization, project, repositoryId, pullRequestId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((":" + accessToken).getBytes()));

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new org.springframework.http.HttpEntity<>(headers), String.class);

        return response;
    }


}
