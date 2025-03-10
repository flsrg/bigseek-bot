# How to obtain the system environment variables

## On Linux/MacOS:

- Temporarily (in your shell session):
```bash
export BIG_SEEK_BOT_TOKEN="your_bot_token_here"
export OPENROUTER_API_KEY="openrouter_token_here"
```

- Permanently:

  Add the export command to your shell profile (e.g., ~/.bashrc, ~/.zshrc).


## On Windows:

- Using Command Prompt:
```cmd
set BIG_SEEK_BOT_TOKEN=your_bot_token_here
set OPENROUTER_API_KEY=openrouter_token_here
```

- Using PowerShell:
```powershell
$Env:BIG_SEEK_BOT_TOKEN="your_bot_token_here"
$Env:OPENROUTER_API_KEY="openrouter_token_here"
```

- IDE Setup:

  In IntelliJ IDEA or Eclipse, add the environment variable in your Run/Debug configuration.

# To build JAR
```declarative
./gradlew shadowJar
```