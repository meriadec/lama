# Lama - Bitcoin REST API

## Getting started
1. Install [sbt][sbt]

## Run the api

> A shared `build.sbt` file is used at the root of the lama project to share common libraries and handle multiple sub projects.
>
>All following sbt commands should be done at the root path of the lama project.

Run the app: `sbt bitcoinApi/run`

### Endpoints

#### Register account
```
POST /accounts
{
    "extendedPublicKey": "account_xpub",
    "scheme": "BIP44",
    "lookaheadSize": 20,
    "network": "MainNet",
    "coinFamily": "bitcoin",
    "coin": "btc"
}

```

#### Get account info
```
GET /accounts/:id
```

#### Get account operations
```
GET /accounts/:id/operations?offset=0&limit=100
```

#### Get account utxos
```
GET /accounts/:id/utxos?offset=0&limit=100
```

### Testing

`sbt bitcoinApi/it:test`

## Docker

The plugin [sbt-docker][sbt-docker] is used to build, run and publish the docker image.

The plugin provides these useful commands:

- `sbt bitcoinApi/docker`:
Builds an image.

- `sbt bitcoinApi/docker:stage`:
Generates a directory with the Dockerfile and environment prepared for creating a Docker image.

- `sbt bitcoinApi/docker:publishLocal`:
Builds an image using the local Docker server.

- `sbt bitcoinApi/docker:publish`
Builds an image using the local Docker server, and pushes it to the configured remote repository.

- `sbt bitcoinApi/docker:clean`
Removes the built image from the local Docker server.

[sbt]: http://www.scala-sbt.org/1.x/docs/Setup.html
