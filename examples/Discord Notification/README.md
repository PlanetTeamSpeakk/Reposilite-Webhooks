# Discord Notification
This example shows how to set up the Reposilite Webhooks plugin in conjunction with [Webhook](https://github.com/adnanh/webhook)
to send messages to Discord.   
**This example requires HMAC_SHA256 payload signing!**  

The default configuration sends the following message to a Discord webhook:  
> 🧪 New build available: **{artifact}** `{version}`  
Changelog: \[{compare_range}]({compare_url})  
Download: \[{filename}]({download_url})

Here, changelog is a GitHub compare URL that compares two version tags. The script uses the GitHub API to find
the tag corresponding to the released version as well as the tag immediately before it to create this comparison URL.

You'll likely only want a notification like this for a regular JAR and not sources or whatever,
for that you can use a regex filter like the following: `com/example/my-cool-project/[^/]+/my-artifact-\d[^-/]*\.jar$`.  
The `[^/]` part matches the version and the `[^-/]` part matches the version in the jar file, while not matching 
`-sources.jar` or any other suffix.  
This filter can be applied either directly to the webhook in Reposilite or to the hook in hooks.json.
