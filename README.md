# DevOps-Pipeline-Compliancy-Automation

# DevOps Pipeline Compliancy Automation 🛠️

A fully automated Spring Boot tool designed to streamline version bumping, pull request creation, and Azure DevOps pipeline approvals across multiple repositories.

## 🚀 Features

- 🔁 Automatically updates `pom.xml` versions
- ✅ Creates and submits pull requests via GitHub REST API
- 🔄 Triggers Azure DevOps pipelines on PR merge
- 🤖 Auto-approves pipelines to maintain compliance
- 📦 Built to scale across many repos with minimal config

## 🏗️ Tech Stack

- **Backend:** Java, Spring Boot
- **Automation:** Azure DevOps REST API, GitHub REST API
- **Build Tool:** Maven
- **Version Control:** Git
- **IDE:** IntelliJ IDEA

### 📁 Project Structure

```text
DevOps-Pipeline-Compliancy-Automation/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── AutomatePipeline/
│   │   │           └── run/
│   │   │               ├── RunApplication.java
│   │   │               ├── controller/
│   │   │               │   └── AzureDevOpsController.java
│   │   │               └── service/
│   │   │                   ├── AzureDevOpsService.java
│   │   │                   └── PomServices/
│   │   │                       └── PomManipulations.java
│   ├── resources/
│   │   └── application.properties
├── test/
│   └── java/
│       └── com/
│           └── AutomatePipeline/
│               └── run/
│                   └── RunApplicationTests.java
├── azure_token.txt
├── github_token.txt
├── repos.txt
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```


## 🧪 How It Works

1. Reads current `pom.xml` version from each repo
2. Increments version automatically
3. Commits changes and creates a PR
4. Triggers Azure DevOps build pipelines
5. Approves pipelines using API auth

   ## 🔧 Configuration

Sensitive config values like API tokens and repository lists are **stored locally** in `.txt` files located in the **same directory as the project**.  
This ensures flexibility and avoids hardcoding credentials into the codebase.

### 🗂️ Required Files (Local Only – Not Committed)

- `azure_token.txt` – contains your Azure DevOps Personal Access Token (PAT)
- `github_token.txt` – contains your GitHub Personal Access Token
- `repos.txt` – list of repository URLs to be processed
