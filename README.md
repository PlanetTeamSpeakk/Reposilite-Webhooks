# Reposilite Webhooks
Adds webhook support to [Reposilite](https://github.com/dzikoysk/reposilite), allowing you to send a POST to whatever endpoint you want when any event is fired.

This plugin is mainly meant to be used in conjunction with [Webhook](https://github.com/adnanh/webhook) to run whatever script you want on some machine.  
Have a look at [the examples](https://github.com/PlanetTeamSpeakk/Reposilite-Webhooks/tree/main/examples) for inspiration
on how to use this plugin.

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
