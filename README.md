# How to obtain the system environment variables

## On Linux/MacOS:

### Temporarily (in your shell session):
```bash
export BIG_SEEK_BOT_TOKEN="your_bot_token_here"
export OPENROUTER_API_KEY="openrouter_token_here"
export BIG_SEEK_BOT_ADMIN_USER_ID="your_user_admin_id"
```
- If you in local setup and want to see debug logs:
```bash
export APP_ENV="dev"
```
  - Otherwise, don't set this variable

### Permanently:

  Add the export command to your shell profile (e.g., ~/.bashrc, ~/.zshrc).


## On Windows:

### Using Command Prompt:
```cmd
set BIG_SEEK_BOT_TOKEN=your_bot_token_here
set OPENROUTER_API_KEY=openrouter_token_here
set BIG_SEEK_BOT_ADMIN_USER_ID="your_user_admin_id"
```
- If you in local setup and want to see debug logs:
```cmd
set APP_ENV="dev"
```
- Otherwise, don't set this variable

### Using PowerShell:
```powershell
$Env:BIG_SEEK_BOT_TOKEN="your_bot_token_here"
$Env:OPENROUTER_API_KEY="openrouter_token_here"
$Env:BIG_SEEK_BOT_ADMIN_USER_ID="your_user_admin_id"
```
- If you in local setup and want to see debug logs:
```cmd
$Env:APP_ENV="dev"
```
- Otherwise, don't set this variable

### IDE Setup:

  In IntelliJ IDEA or Eclipse, add the environment variable in your Run/Debug configuration.

# To build JAR
```bash
./gradlew shadowJar
```