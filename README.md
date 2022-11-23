# interop-be-purpose-management

To change the log level at runtime use:

```
# This is for the internal Akka actors and classic Akka logging
curl -X PUT <akkaManagementEndpoint>/loglevel/akka?level=DEBUG
# This is for the internal Akka actors and classic Akka logging
curl -X PUT <akkaManagementEndpoint>/loglevel/logback?logger=<loggerName>&level=DEBUG
```

where `<loggerName>` is the name of a specific logger that has to have been created in the code itself.
`GET`ting the same endpoints w/o the `level` query parameters returns the actual logging level for that logger.

[Source](https://doc.akka.io/docs/akka-management/current/loglevels/logback.html) 