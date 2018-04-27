async function main() {
	const body = document.getElementById('messages');

	const data = await fetch('/services/export');
	if (data.status >= 400 && data.status < 500) {
		const text = await
		data.text();
		body.append(text);
		document.getElementById('form').style.display = 'block';
	} else {
		const jsonStr = await data.text();
		jsonObj = JSON.parse(jsonStr);
		for ( var item in jsonObj.reportItems) {
			//TODO: This is a quick way to make things more readable, needs to be upgraded to angular.js
			var color = '#';
			switch(jsonObj.reportItems[item].type){
			case "WARNING":
				color += 'F39C12'
				break;
			case "ERROR":
				color += 'F22613'
				break;
			case "INFO":
				color += '4183D7'
				break;
			}
			
			$('p#messages').append(
					'<span style="color:' + color + '">[' + jsonObj.reportItems[item].type + ']</span>: ' + jsonObj.reportItems[item].message + '<br/>');
		}
		
		var parser = new Parser();
		for (var stub in jsonObj.stubs) {
			try {
				parser.parse(jsonObj.stubs[stub], 0);
			} catch (e) {
				$('p#messages').append('<span style="color:#F39C12">[WARNING]</span>: Stub error ' + stub + " - " + e.name + '<br/>')
				for (var key in e.params) {
					if (e.params.hasOwnProperty(key)) {
						$('p#messages').append('&emsp;' +
								key + ': ' + e.params[key] + '<br/>');
					}
				}
			}
		}

		if (jsonObj.exportStatus === "SUCCESS") {
			body.append('Export success.');
			var url = window.URL.createObjectURL(new Blob(
					_base64ToArrayBuffer(jsonObj.data)));
			var a = document.createElement('a');
			a.href = url;
			a.download = "export.zip";
			a.click();
		} else {
			body.append('Export fail.');
		}
	}
}

function _base64ToArrayBuffer(base64) {
	var binary_string = window.atob(base64);
	var len = binary_string.length;
	var bytes = new Uint8Array(len);
	for (var i = 0; i < len; i++) {
		bytes[i] = binary_string.charCodeAt(i);
	}
	return [ bytes ];
}