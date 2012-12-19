To update the [status page](http://status.cgeo.org/) with the new release information, you need to include the following step in your nightly build process, at the end, when the executable is ready to be downloaded:

```bash
curl -X POST http://status.cgeo.org/api/update/nightly --data "key=SECRET" --data "version_code=YYYYMMDD" --data "version_name=YYYY.MM.DD-NB-SHA1"
```

I will send you `SECRET` in a private message.

Same for release candidates, using

```bash
curl -X POST http://status.cgeo.org/api/update/rc --data "key=SECRET" --data "version_code=YYYYMMDD" --data "version_name=YYYY.MM.DD-RC-SHA1"
```

And, for a plain release on Market, once it has appeared on Google Play:

```bash
curl -X POST http://status.cgeo.org/api/update/release --data "key=SECRET" --data "version_code=YYYYMMDD" --data "version_name=YYYY.MM.DD"
```
Once a release has been done, you can remove the release candidate information by using:

```bash
curl -X DELETE "http://status.cgeo.org/api/update/rc?key=SECRET"
```

You can also update the message sent to up-to-date clients with:

```bash
curl -X POST http://status.cgeo.org/api/update/message --data "key=SECRET" --data "message=This is the message" --data "icon=attribute_danger" --data "message_id=emergency_message" --data "url=http://www.cgeo.org/"
```

Only `message` is mandatory. `icon` must be an existing attribute in the application, `message_id` may override `message` if the resource can be found in the application (it would be localizable), `url` would make the message clickable.

Similarly, the message is removed using:

```bash
curl -X DELETE "http://status.cgeo.org/api/update/message?key=SECRET"
```
