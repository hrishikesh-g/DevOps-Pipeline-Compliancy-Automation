# DevOps Pipeline Compliancy Automation 🛠️

This application automates the end-to-end DevOps pipeline compliance process across multiple repositories, enabling mass-scale versioning, PR handling, and pipeline triggering in a fully hands-off fashion uding Spring Boot, AzureDevOps API & GIT API.

## 🚀 Features

⚙️ Features:

📦 Clones repos locally from repos.txt
🔧 Bumps pom.xml version automatically
🌿 Pushes changes to a temp branch
🔁 Creates PRs with auto-complete enabled
✅ Auto-approves PRs using authorized PAT
🧹 Deletes remote temp branches post-merge
🧵 Uses multithreading for concurrent repo processing
🚀 Triggers all repo pipelines simultaneously

## 🏗️ Tech Stack

- **Backend:** Java, Spring Boot
- **Automation:** Azure DevOps REST API, GitHub REST API
- **Build Tool:** Maven
- **Multithreading:** Executable Future
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
