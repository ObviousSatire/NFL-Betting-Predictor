from flask import Flask, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app, resources={r"/*": {"origins": "*"}})

PORT = 5000
PC_IP = "10.182.114.244"

@app.route('/test', methods=['GET'])
def test():
    return jsonify({
        "status": "success",
        "message": "API working!",
        "ip": PC_IP,
        "port": PORT
    })

@app.route('/predict', methods=['GET'])
def predict():
    games = [
        {"game": "Chiefs vs Ravens", "prediction": "Chiefs win", "confidence": 0.85},
        {"game": "49ers vs Cowboys", "prediction": "49ers win", "confidence": 0.72},
        {"game": "Eagles vs Giants", "prediction": "Eagles win", "confidence": 0.91}
    ]
    return jsonify({"predictions": games})

if __name__ == '__main__':
    print(f"\n?? API running at: http://{PC_IP}:{PORT}/test\n")
    app.run(host='0.0.0.0', port=PORT, debug=True)
