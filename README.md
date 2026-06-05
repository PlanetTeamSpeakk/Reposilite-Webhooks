# Reposilite Webhooks
Adds webhook support to [Reposilite](https://github.com/dzikoysk/reposilite), allowing you to send a POST to whatever endpoint you want when any event is fired.

This plugin is mainly meant to be used in conjunction with [Webhook](https://github.com/adnanh/webhook) to run whatever script you want on some machine.
An example configuration that runs some script with the artifact path (in the form `/some_repository/some/group/my_artifact/some_version/my_artifact.jar`) on a DEPLOY event might look like this:
```json
[
  {
    "id": "my-webhook",
    "execute-command": "/opt/scripts/my_script.sh",
    "command-working-directory": "/tmp",
    "pass-arguments-to-command": [
      {
        "source": "payload",
        "name": "data.path"
      }
    ],
    "trigger-rule": {
      "and": [
        {
          "match": {
            "type": "payload-hmac-sha256",
            "secret": "<WEBHOOK SECRET>",
            "parameter": {
              "source": "header",
              "name": "X-Hub-Signature-256"
            }
          }
        },
        {
          "match": {
            "type": "value",
            "value": "DEPLOY",
            "parameter": {
              "source": "payload",
              "name": "event"
            }
          }
        }
      ]
    }
  }
]
```

The data passed to the webhook looks as follows:
```json
{
  "event": "DEPLOY",
  "deliveryId": "<some uuid>",
  "timestamp": <epoch timestamp in milliseconds>
  "data": <data object or null if no data>
}
```
When choosing www-form-urlencoded as the body type, the data property will be a JSON encoded string. The delivery ID may be used to prevent retries from being processed multiple times, if desired.
