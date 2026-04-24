import requests
from flask import Flask, jsonify, request
from flask_cors import CORS
from datetime import datetime
import time

app = Flask(__name__)
CORS(app)

try:
    from sportsipy.nfl.roster import Roster
    SPORTSIPY_AVAILABLE = True
    print("Sportsipy loaded successfully")
except ImportError:
    SPORTSIPY_AVAILABLE = False
    print("Sportsipy not available")

TOMORROW_API_KEY = "4jUTfSnJ2t7VJjG5zRKh5gR6K8m2JhyM"

cache = {}
cache_timeout = 300
ESPN_SITE_V2 = "https://site.api.espn.com/apis/site/v2/sports/football/nfl"

# Manual overrides storage
manual_overrides = {}

STADIUM_LOCATIONS = {
    "Buffalo Bills": {"city": "Buffalo", "lat": 42.7738, "lon": -78.7870, "stadium": "Highmark Stadium"},
    "Miami Dolphins": {"city": "Miami", "lat": 25.9580, "lon": -80.2389, "stadium": "Hard Rock Stadium"},
    "New England Patriots": {"city": "Foxborough", "lat": 42.0909, "lon": -71.2643, "stadium": "Gillette Stadium"},
    "New York Jets": {"city": "East Rutherford", "lat": 40.8136, "lon": -74.0744, "stadium": "MetLife Stadium"},
    "Baltimore Ravens": {"city": "Baltimore", "lat": 39.2780, "lon": -76.6227, "stadium": "M&T Bank Stadium"},
    "Cincinnati Bengals": {"city": "Cincinnati", "lat": 39.0954, "lon": -84.5161, "stadium": "Paycor Stadium"},
    "Cleveland Browns": {"city": "Cleveland", "lat": 41.5061, "lon": -81.6995, "stadium": "Huntington Bank Field"},
    "Pittsburgh Steelers": {"city": "Pittsburgh", "lat": 40.4468, "lon": -80.0158, "stadium": "Acrisure Stadium"},
    "Houston Texans": {"city": "Houston", "lat": 29.6847, "lon": -95.4107, "stadium": "NRG Stadium"},
    "Indianapolis Colts": {"city": "Indianapolis", "lat": 39.7601, "lon": -86.1639, "stadium": "Lucas Oil Stadium"},
    "Jacksonville Jaguars": {"city": "Jacksonville", "lat": 30.3240, "lon": -81.6375, "stadium": "EverBank Stadium"},
    "Tennessee Titans": {"city": "Nashville", "lat": 36.1664, "lon": -86.7714, "stadium": "Nissan Stadium"},
    "Denver Broncos": {"city": "Denver", "lat": 39.7439, "lon": -105.0201, "stadium": "Empower Field"},
    "Kansas City Chiefs": {"city": "Kansas City", "lat": 39.0489, "lon": -94.4839, "stadium": "Arrowhead Stadium"},
    "Las Vegas Raiders": {"city": "Las Vegas", "lat": 36.0909, "lon": -115.1836, "stadium": "Allegiant Stadium"},
    "Los Angeles Chargers": {"city": "Inglewood", "lat": 33.9535, "lon": -118.3394, "stadium": "SoFi Stadium"},
    "Dallas Cowboys": {"city": "Arlington", "lat": 32.7473, "lon": -97.0945, "stadium": "AT&T Stadium"},
    "New York Giants": {"city": "East Rutherford", "lat": 40.8136, "lon": -74.0744, "stadium": "MetLife Stadium"},
    "Philadelphia Eagles": {"city": "Philadelphia", "lat": 39.9011, "lon": -75.1677, "stadium": "Lincoln Financial Field"},
    "Washington Commanders": {"city": "Landover", "lat": 38.9076, "lon": -76.8645, "stadium": "FedExField"},
    "Chicago Bears": {"city": "Chicago", "lat": 41.8625, "lon": -87.6167, "stadium": "Soldier Field"},
    "Detroit Lions": {"city": "Detroit", "lat": 42.3400, "lon": -83.0456, "stadium": "Ford Field"},
    "Green Bay Packers": {"city": "Green Bay", "lat": 44.5013, "lon": -88.0622, "stadium": "Lambeau Field"},
    "Minnesota Vikings": {"city": "Minneapolis", "lat": 44.9739, "lon": -93.2577, "stadium": "U.S. Bank Stadium"},
    "Atlanta Falcons": {"city": "Atlanta", "lat": 33.7550, "lon": -84.4000, "stadium": "Mercedes-Benz Stadium"},
    "Carolina Panthers": {"city": "Charlotte", "lat": 35.2258, "lon": -80.8528, "stadium": "Bank of America Stadium"},
    "New Orleans Saints": {"city": "New Orleans", "lat": 29.9509, "lon": -90.0811, "stadium": "Caesars Superdome"},
    "Tampa Bay Buccaneers": {"city": "Tampa", "lat": 27.9760, "lon": -82.5033, "stadium": "Raymond James Stadium"},
    "Arizona Cardinals": {"city": "Glendale", "lat": 33.5275, "lon": -112.2625, "stadium": "State Farm Stadium"},
    "Los Angeles Rams": {"city": "Inglewood", "lat": 33.9535, "lon": -118.3394, "stadium": "SoFi Stadium"},
    "San Francisco 49ers": {"city": "Santa Clara", "lat": 37.4031, "lon": -121.9698, "stadium": "Levi's Stadium"},
    "Seattle Seahawks": {"city": "Seattle", "lat": 47.5952, "lon": -122.3316, "stadium": "Lumen Field"}
}

TEAM_MAP = {
    "Buffalo Bills": "buf", "Miami Dolphins": "mia", "New England Patriots": "ne", "New York Jets": "nyj",
    "Baltimore Ravens": "bal", "Cincinnati Bengals": "cin", "Cleveland Browns": "cle", "Pittsburgh Steelers": "pit",
    "Houston Texans": "hou", "Indianapolis Colts": "ind", "Jacksonville Jaguars": "jax", "Tennessee Titans": "ten",
    "Denver Broncos": "den", "Kansas City Chiefs": "kc", "Las Vegas Raiders": "lv", "Los Angeles Chargers": "lac",
    "Dallas Cowboys": "dal", "New York Giants": "nyg", "Philadelphia Eagles": "phi", "Washington Commanders": "wsh",
    "Chicago Bears": "chi", "Detroit Lions": "det", "Green Bay Packers": "gb", "Minnesota Vikings": "min",
    "Atlanta Falcons": "atl", "Carolina Panthers": "car", "New Orleans Saints": "no", "Tampa Bay Buccaneers": "tb",
    "Arizona Cardinals": "ari", "Los Angeles Rams": "lar", "San Francisco 49ers": "sf", "Seattle Seahawks": "sea"
}

TEAM_KEYWORDS = {
    "Buffalo Bills": ["Bills", "Buffalo"], "Miami Dolphins": ["Dolphins", "Miami"],
    "New England Patriots": ["Patriots", "New England"], "New York Jets": ["Jets"],
    "Baltimore Ravens": ["Ravens", "Baltimore"], "Cincinnati Bengals": ["Bengals", "Cincinnati"],
    "Cleveland Browns": ["Browns", "Cleveland"], "Pittsburgh Steelers": ["Steelers", "Pittsburgh"],
    "Houston Texans": ["Texans", "Houston"], "Indianapolis Colts": ["Colts", "Indianapolis"],
    "Jacksonville Jaguars": ["Jaguars", "Jacksonville"], "Tennessee Titans": ["Titans", "Tennessee"],
    "Denver Broncos": ["Broncos", "Denver"], "Kansas City Chiefs": ["Chiefs", "Kansas City"],
    "Las Vegas Raiders": ["Raiders", "Las Vegas"], "Los Angeles Chargers": ["Chargers"],
    "Dallas Cowboys": ["Cowboys", "Dallas"], "New York Giants": ["Giants"],
    "Philadelphia Eagles": ["Eagles", "Philadelphia"], "Washington Commanders": ["Commanders", "Washington"],
    "Chicago Bears": ["Bears", "Chicago"], "Detroit Lions": ["Lions", "Detroit"],
    "Green Bay Packers": ["Packers", "Green Bay"], "Minnesota Vikings": ["Vikings", "Minnesota"],
    "Atlanta Falcons": ["Falcons", "Atlanta"], "Carolina Panthers": ["Panthers", "Carolina"],
    "New Orleans Saints": ["Saints", "New Orleans"], "Tampa Bay Buccaneers": ["Buccaneers", "Bucs", "Tampa Bay"],
    "Arizona Cardinals": ["Cardinals", "Arizona"], "Los Angeles Rams": ["Rams"],
    "San Francisco 49ers": ["49ers", "San Francisco", "Niners"], "Seattle Seahawks": ["Seahawks", "Seattle"]
}

def get_cached_or_fetch(url, cache_key):
    if cache_key in cache:
        data, timestamp = cache[cache_key]
        if time.time() - timestamp < cache_timeout:
            return data
    try:
        response = requests.get(url, timeout=10)
        if response.status_code == 200:
            data = response.json()
            cache[cache_key] = (data, time.time())
            return data
        return None
    except:
        return None

def get_weather_from_api(team_name):
    if team_name not in STADIUM_LOCATIONS:
        return None
    location = STADIUM_LOCATIONS[team_name]
    try:
        url = "https://api.tomorrow.io/v4/timelines"
        params = {
            "location": f"{location['lat']},{location['lon']}",
            "fields": ["temperature", "weatherCode", "windSpeed", "precipitationProbability"],
            "timesteps": "current", "units": "imperial", "apikey": TOMORROW_API_KEY
        }
        response = requests.get(url, params=params, timeout=10)
        if response.status_code == 200:
            data = response.json()
            current = data['data']['timelines'][0]['intervals'][0]['values']
            weather_codes = {1000: "Clear", 1100: "Mostly Clear", 1101: "Partly Cloudy", 1102: "Mostly Cloudy",
                1001: "Cloudy", 4000: "Rain", 4200: "Light Rain", 4201: "Heavy Rain",
                5000: "Snow", 5100: "Light Snow", 5101: "Heavy Snow", 8000: "Thunderstorm"}
            conditions = weather_codes.get(current.get('weatherCode', 1000), "Unknown")
            wind = current.get('windSpeed', 0)
            precip = current.get('precipitationProbability', 0)
            temp = current.get('temperature', 45)
            impact = "Minimal impact"
            if wind > 20: impact = f"HIGH WIND ({wind} mph)"
            elif wind > 15: impact = f"Moderate wind ({wind} mph)"
            elif precip > 70: impact = f"Heavy rain ({precip}%)"
            elif temp < 32: impact = f"Freezing ({temp}F)"
            return {"temperature": round(temp), "conditions": conditions, "wind_speed": round(wind),
                "precipitation": precip, "city": location["city"], "stadium": location["stadium"], "impact": impact}
        return None
    except:
        return None

def get_player_stats_sportsipy(player_name, team_name):
    if not SPORTSIPY_AVAILABLE:
        return None
    try:
        team_abbr_map = {
            "Buffalo Bills": "BUF", "Miami Dolphins": "MIA", "New England Patriots": "NE",
            "New York Jets": "NYJ", "Baltimore Ravens": "BAL", "Cincinnati Bengals": "CIN",
            "Cleveland Browns": "CLE", "Pittsburgh Steelers": "PIT", "Houston Texans": "HOU",
            "Indianapolis Colts": "IND", "Jacksonville Jaguars": "JAX", "Tennessee Titans": "TEN",
            "Denver Broncos": "DEN", "Kansas City Chiefs": "KC", "Las Vegas Raiders": "LV",
            "Los Angeles Chargers": "LAC", "Dallas Cowboys": "DAL", "New York Giants": "NYG",
            "Philadelphia Eagles": "PHI", "Washington Commanders": "WAS", "Chicago Bears": "CHI",
            "Detroit Lions": "DET", "Green Bay Packers": "GB", "Minnesota Vikings": "MIN",
            "Atlanta Falcons": "ATL", "Carolina Panthers": "CAR", "New Orleans Saints": "NO",
            "Tampa Bay Buccaneers": "TB", "Arizona Cardinals": "ARI", "Los Angeles Rams": "LAR",
            "San Francisco 49ers": "SF", "Seattle Seahawks": "SEA"
        }
        team_abbr = team_abbr_map.get(team_name)
        if not team_abbr:
            return None
        print(f"[SPORTSIPY] Looking for {player_name} on {team_abbr}...")
        roster = Roster(team_abbr)
        for player in roster.players:
            if player_name.lower() in player.name.lower():
                print(f"[SPORTSIPY] Found: {player.name}")
                stats = {}
                try:
                    stats['passing_yards'] = int(player.passing_yards or 0)
                    stats['passing_tds'] = int(player.passing_touchdowns or 0)
                    stats['completions'] = int(player.passing_completions or 0)
                    stats['passing_attempts'] = int(player.passing_attempts or 0)
                    stats['interceptions'] = int(player.passing_interceptions or 0)
                    stats['qb_rating'] = float(player.quarterback_rating or 0)
                except: pass
                try:
                    stats['rushing_yards'] = int(player.rushing_yards or 0)
                    stats['rushing_tds'] = int(player.rushing_touchdowns or 0)
                    stats['rushing_attempts'] = int(player.rushing_attempts or 0)
                except: pass
                try:
                    stats['receiving_yards'] = int(player.receiving_yards or 0)
                    stats['receiving_tds'] = int(player.receiving_touchdowns or 0)
                    stats['receptions'] = int(player.receptions or 0)
                    stats['targets'] = int(player.receiving_targets or 0)
                except: pass
                print(f"[SPORTSIPY] Stats: {stats}")
                return stats if stats else None
    except Exception as e:
        print(f"[SPORTSIPY] Error: {e}")
    return None

def get_mock_player_stats(player_id, player_name):
    """No mock data - return None when real stats unavailable"""
    return None

@app.route('/test', methods=['GET'])
def test():
    return jsonify({"status": "ok", "sportsipy_available": SPORTSIPY_AVAILABLE})

@app.route('/team_stats', methods=['GET'])
def get_team_stats():
    team = request.args.get('team')
    if team not in TEAM_MAP:
        return jsonify({"error": "Team not found"}), 404
    
    # Check manual overrides first
    if team in manual_overrides and manual_overrides[team]:
        override = manual_overrides[team]
        return jsonify({
            "team": team,
            "record": override.get("record", "0-0"),
            "points_for": int(override.get("points_for", 0)),
            "points_against": int(override.get("points_against", 0)),
            "streak": override.get("streak", "N/A")
        })
    
    team_abbr = TEAM_MAP[team]
    url = f"{ESPN_SITE_V2}/teams/{team_abbr}"
    data = get_cached_or_fetch(url, f"team_{team_abbr}")
    if not data:
        return jsonify({"error": "Could not fetch team data"}), 500
    stats = {"team": team, "record": "0-0", "points_for": 0, "points_against": 0, "streak": "N/A"}
    if 'team' in data and 'record' in data['team']:
        for item in data['team']['record'].get('items', []):
            if item.get('type') == 'total':
                stats['record'] = item.get('summary', '0-0')
                for stat in item.get('stats', []):
                    name = stat.get('name', '')
                    if name == 'pointsFor': stats['points_for'] = int(stat.get('value', 0))
                    elif name == 'pointsAgainst': stats['points_against'] = int(stat.get('value', 0))
                    elif name == 'streak':
                        streak_val = int(stat.get('value', 0))
                        stats['streak'] = f"+{streak_val}" if streak_val > 0 else f"{streak_val}" if streak_val < 0 else "Even"
                break
    return jsonify(stats)

@app.route('/roster', methods=['GET'])
def get_roster():
    team_name = request.args.get('team', '')
    if not team_name or team_name not in TEAM_MAP:
        return jsonify({"error": "Team required"}), 400
    url = f"{ESPN_SITE_V2}/teams/{TEAM_MAP[team_name]}/roster"
    try:
        response = requests.get(url, timeout=10)
        if response.status_code != 200:
            return jsonify({"error": "Failed to get roster"}), 500
        data = response.json()
        players = []
        if 'athletes' in data:
            for group in data['athletes']:
                for player in group.get('items', []):
                    name = player.get('fullName', '')
                    if name:
                        players.append({
                            "id": player.get('id', ''),
                            "name": name,
                            "position": player.get('position', {}).get('abbreviation', ''),
                            "jersey": player.get('jersey', ''),
                            "injured": player.get('injured', False)
                        })
        return jsonify({"team": team_name, "players": players})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/player_stats', methods=['GET'])
def get_player_stats():
    name = request.args.get('name')
    team = request.args.get('team')
    if not name:
        return jsonify({"error": "Player name required"}), 400
    player_id = None
    player_info = {}
    if team and team in TEAM_MAP:
        url = f"{ESPN_SITE_V2}/teams/{TEAM_MAP[team]}/roster"
        data = get_cached_or_fetch(url, f"roster_{team}")
        if data and 'athletes' in data:
            for group in data['athletes']:
                for player in group.get('items', []):
                    if name.lower() in player.get('fullName', '').lower():
                        player_id = player.get('id', '')
                        player_info = {
                            "id": player_id,
                            "name": player.get('fullName'),
                            "position": player.get('position', {}).get('abbreviation'),
                            "jersey": player.get('jersey'),
                            "team": team,
                            "injured": player.get('injured', False)
                        }
                        break
    if not player_id:
        return jsonify({"error": "Player not found"}), 404
    stats = get_player_stats_sportsipy(name, team)
    if stats:
        player_info["stats"] = stats
    else:
        player_info["stats"] = None
    return jsonify(player_info)

@app.route('/weather', methods=['GET'])
def get_weather():
    team = request.args.get('team')
    if not team:
        return jsonify({"error": "Team parameter required"}), 400
    weather = get_weather_from_api(team)
    if weather:
        return jsonify(weather), 200
    else:
        return jsonify({
            "city": "Unknown", "stadium": team, "temperature": 65,
            "conditions": "Clear", "wind_speed": 5, "precipitation": 0, "impact": "Minimal impact"
        }), 200

@app.route('/injuries', methods=['GET'])
def get_injuries():
    team = request.args.get('team')
    url = f"{ESPN_SITE_V2}/injuries"
    data = get_cached_or_fetch(url, "all_injuries")
    if not data:
        return jsonify({"error": "Could not fetch injuries"}), 500
    team_injuries = []
    for t in data.get('injuries', []):
        if team and team.lower() in t.get('displayName', '').lower():
            for inj in t.get('injuries', []):
                team_injuries.append({
                    "player": inj.get('athlete', {}).get('fullName', 'Unknown'),
                    "position": inj.get('athlete', {}).get('position', {}).get('abbreviation', 'N/A'),
                    "injury": inj.get('injury', {}).get('type', 'Unknown'),
                    "status": inj.get('status', 'Unknown'),
                    "date": inj.get('date', '')
                })
            break
    return jsonify({"team": team or "All Teams", "injuries": team_injuries})

@app.route('/news', methods=['GET'])
def get_news():
    entity = request.args.get('entity', '')
    url = f"{ESPN_SITE_V2}/news"
    try:
        response = requests.get(url, timeout=10)
        if response.status_code != 200:
            return jsonify({"team": entity or "All Teams", "news": []})
        data = response.json()
        articles = []
        team_keywords = TEAM_KEYWORDS.get(entity, []) if entity else []
        if 'articles' in data:
            for article in data['articles'][:30]:
                headline = article.get('headline', '')
                description = article.get('description', '')
                if entity and team_keywords:
                    categories = article.get('categories', [])
                    team_match = False
                    for cat in categories:
                        if cat.get('type') == 'team':
                            cat_team = cat.get('description', '')
                            if any(kw.lower() in cat_team.lower() for kw in team_keywords):
                                team_match = True
                                break
                    if not team_match:
                        text = (headline + " " + description).lower()
                        if any(kw.lower() in text for kw in team_keywords):
                            team_match = True
                    if not team_match:
                        continue
                pub = article.get('published', '')
                articles.append({
                    "headline": headline or 'No headline',
                    "date": pub.split('T')[0] if pub else '',
                    "description": description or '',
                    "source": "ESPN"
                })
        if entity and not articles and data.get('articles'):
            for article in data['articles'][:5]:
                pub = article.get('published', '')
                articles.append({
                    "headline": article.get('headline', 'No headline'),
                    "date": pub.split('T')[0] if pub else '',
                    "description": article.get('description', ''),
                    "source": "ESPN (General NFL)"
                })
        return jsonify({"team": entity or "All Teams", "news": articles})
    except Exception as e:
        print(f"News error: {e}")
        return jsonify({"team": entity or "All Teams", "news": [], "error": str(e)})

@app.route('/win_loss', methods=['GET'])
def get_win_loss():
    team = request.args.get('team')
    if team not in TEAM_MAP:
        return jsonify({"error": "Team not found"}), 404
    abbr = TEAM_MAP[team]
    url = f"{ESPN_SITE_V2}/teams/{abbr}"
    data = get_cached_or_fetch(url, f"team_{abbr}")
    if not data:
        return jsonify({"error": "Could not fetch team data"}), 500
    reg_record = "0-0"
    win_pct = 0.0
    streak = "N/A"
    home = "N/A"
    away = "N/A"
    if 'team' in data and 'record' in data['team']:
        for item in data['team']['record'].get('items', []):
            if item.get('type') == 'total':
                reg_record = item.get('summary', '0-0')
                for stat in item.get('stats', []):
                    n = stat.get('name', '')
                    if n == 'streak':
                        v = int(stat.get('value', 0))
                        streak = f"+{v}" if v > 0 else f"{v}" if v < 0 else "Even"
                    elif n == 'homeRecord': home = stat.get('displayValue', 'N/A')
                    elif n == 'awayRecord': away = stat.get('displayValue', 'N/A')
                break
    if '-' in reg_record:
        w, l = map(int, reg_record.split('-'))
        if w + l > 0:
            win_pct = w / (w + l)
    return jsonify({
        "regular_season_record": reg_record, "win_percentage": win_pct,
        "current_streak": streak, "last_5_games": [],
        "home_record": home, "away_record": away
    })

@app.route('/predict', methods=['GET'])
def predict_winner():
    team1 = request.args.get('team1')
    team2 = request.args.get('team2')
    if not team1 or not team2 or team1 not in TEAM_MAP or team2 not in TEAM_MAP:
        return jsonify({"error": "Invalid teams"}), 400
    def get_win_pct(t):
        url = f"{ESPN_SITE_V2}/teams/{TEAM_MAP[t]}"
        data = get_cached_or_fetch(url, f"team_{TEAM_MAP[t]}")
        if data and 'team' in data:
            for item in data['team']['record'].get('items', []):
                if item.get('type') == 'total':
                    rec = item.get('summary', '0-0')
                    if '-' in rec:
                        w, l = map(int, rec.split('-'))
                        if w + l > 0: return w / (w + l)
        return 0.5
    p1 = get_win_pct(team1)
    p2 = get_win_pct(team2)
    total = p1 + p2
    prob1 = (p1 / total * 100) if total > 0 else 50.0
    prob2 = (p2 / total * 100) if total > 0 else 50.0
    return jsonify({
        "team1": team1, "team2": team2,
        "team1_win_probability": round(prob1, 1), "team2_win_probability": round(prob2, 1),
        "predicted_winner": team1 if prob1 > prob2 else team2,
        "confidence": round(abs(prob1 - prob2), 1),
        "key_factors": [f"{team1} record: {p1:.1%}", f"{team2} record: {p2:.1%}"]
    })

@app.route('/manual_override', methods=['POST'])
def set_manual_override():
    data = request.get_json()
    team = data.get('team')
    stat = data.get('stat')
    value = data.get('value')
    if not team or not stat or value is None:
        return jsonify({"error": "team, stat, value required"}), 400
    if team not in manual_overrides:
        manual_overrides[team] = {}
    manual_overrides[team][stat] = str(value)
    return jsonify({"status": "ok", "team": team, "overrides": manual_overrides[team]})

@app.route('/manual_overrides', methods=['GET'])
def get_manual_overrides():
    team = request.args.get('team')
    if team:
        return jsonify({team: manual_overrides.get(team, {})})
    return jsonify(manual_overrides)

if __name__ == '__main__':
    print("NFL API Running on http://localhost:5000")
    app.run(host='0.0.0.0', port=5000, debug=True)