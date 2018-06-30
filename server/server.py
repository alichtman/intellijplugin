from flask import Flask, jsonify, request, abort

app = Flask(__name__)
DEBUG = True

## Routes ##

@app.route('/plugin/api/upload_status', methods=['POST'])
def process_status():
	print("PROCESSING", request.json)
	return jsonify(request.json)

## Main ##


if __name__ == "__main__":
	app.run(debug=DEBUG)

