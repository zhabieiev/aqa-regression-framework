# Running JHipster API and UI

Run all commands from the `jhipster-sample-app` root directory in PowerShell.

## Start an existing stopped container

```powershell
docker compose -f .\src\main\docker\app.yml start
```

## Start after `docker compose down`

```powershell
docker compose -f .\src\main\docker\app.yml up -d
```

## Rebuild after application changes or if the Docker image is missing

Build the Docker image:

```powershell
.\mvnw.cmd -ntp verify "-DskipTests" "-Pprod,api-docs" jib:dockerBuild
```

Start and recreate the container:

```powershell
docker compose -f .\src\main\docker\app.yml up -d --force-recreate
```

## Verify the application

Check the container status:

```powershell
docker compose -f .\src\main\docker\app.yml ps
```

View application logs:

```powershell
docker compose -f .\src\main\docker\app.yml logs -f
```

Available endpoints:

* UI: http://localhost:8080
* Swagger UI: http://localhost:8080/admin/docs
* OpenAPI specification: http://localhost:8080/v3/api-docs
* API base URL: http://localhost:8080/api

Default credentials:

* Administrator: `admin` / `admin`
* User: `user` / `user`
