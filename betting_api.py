import requests
import joblib
import pandas as pd
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
    player_info = {"name": name, "team": team, "position": "N/A", "jersey": "N/A", "injured": False, "stats": None}
    
    # Step 1: Try roster lookup for player info
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
                            "injured": player.get('injured', False),
                            "stats": None
                        }
                        break
    
    # Step 2: Try Sportsipy for real season stats
    s_stats = get_player_stats_sportsipy(name, team)
    if s_stats:
        player_info["stats"] = s_stats
        return jsonify(player_info)
    
    # Step 3: Search boxscores (2026 pre/reg first, then 2025 reg/pre)
    for season, seasontype in [(2026, 1), (2026, 2), (2025, 2), (2025, 1)]:
        sched_url = f'{ESPN_SITE_V2}/teams/{TEAM_MAP.get(team, "")}/schedule?season={season}&seasontype={seasontype}'
        try:
            sched = requests.get(sched_url, timeout=10).json()
        except:
            continue
        # Sort by date descending - most recent game first
        events = sched.get('events', [])
        for event in reversed(events):
            event_id = event.get('id', '')
            if not event_id:
                continue
            comp = event.get('competitions', [{}])[0]
            status = comp.get('status', {}).get('type', {})
            if not status.get('completed'):
                continue
            try:
                summary = requests.get(f'{ESPN_SITE_V2}/summary?event={event_id}', timeout=10).json()
            except:
                continue
            if 'boxscore' not in summary:
                continue
            for team_data in summary['boxscore'].get('players', []):
                for stat_group in team_data.get('statistics', []):
                    for athlete in stat_group.get('athletes', []):
                        athlete_name = athlete.get('athlete', {}).get('displayName', '')
                        if name.lower() in athlete_name.lower():
                            stats = {}
                            raw_stats = athlete.get('stats', [])
                            if len(raw_stats) >= 2:
                                if stat_group['name'] == 'passing':
                                    try:
                                        stats['completions'] = raw_stats[0].split('/')[0]
                                        stats['passing_attempts'] = raw_stats[0].split('/')[1]
                                        stats['passing_yards'] = int(raw_stats[1])
                                        if len(raw_stats) >= 5:
                                            stats['passing_tds'] = int(raw_stats[3])
                                    except: pass
                                elif stat_group['name'] == 'rushing':
                                    try:
                                        stats['rushing_attempts'] = int(raw_stats[0]) if raw_stats[0].isdigit() else 0
                                        stats['rushing_yards'] = int(raw_stats[1])
                                        if len(raw_stats) >= 4:
                                            stats['rushing_tds'] = int(raw_stats[3])
                                    except: pass
                                elif stat_group['name'] == 'receiving':
                                    try:
                                        stats['receptions'] = int(raw_stats[0]) if raw_stats[0].isdigit() else 0
                                        stats['receiving_yards'] = int(raw_stats[1])
                                        if len(raw_stats) >= 4:
                                            stats['receiving_tds'] = int(raw_stats[3])
                                    except: pass
                            if stats:
                                player_info["stats"] = stats
                                player_info["stats_source"] = f"{season} season, week {event.get('week', {}).get('number', 'N/A')}"
                                if not player_id:
                                    player_info["id"] = str(athlete.get('athlete', {}).get('id', ''))
                                    player_info["name"] = athlete_name
                                return jsonify(player_info)
    
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
    if not team1 or not team2:
        return jsonify({"error": "Invalid teams"}), 400
    
    try:
        # Load ML models
        rf = joblib.load('ml_models/random_forest.pkl')
        gb = joblib.load('ml_models/gradient_boosting.pkl')
        strengths = joblib.load('ml_models/team_strengths.pkl')
    except:
        rf = gb = None
        strengths = {}
    
    t1_abbr = TEAM_MAP.get(team1, '').upper()
    t2_abbr = TEAM_MAP.get(team2, '').upper()
    
    # Fetch live stats from ESPN
    def count_schedule(abbr, season, seasontype):
        sched_url = f"{ESPN_SITE_V2}/teams/{abbr}/schedule?season={season}&seasontype={seasontype}"
        sched = get_cached_or_fetch(sched_url, f"sched_{season}_{seasontype}_{abbr}")
        w = l = pf = pa = 0
        if sched and 'events' in sched:
            for ev in sched['events']:
                comp = ev.get('competitions', [{}])[0]
                status = comp.get('status', {}).get('type', {})
                if not status.get('completed'):
                    continue
                for c in comp.get('competitors', []):
                    if c.get('team', {}).get('abbreviation', '').upper() == abbr.upper():
                        sv = c.get('score', 0)
                        if isinstance(sv, dict): sv = sv.get('value', 0)
                        sv = int(sv) if sv else 0
                        opp_score = 0
                        for o in comp.get('competitors', []):
                            if o is not c:
                                ov = o.get('score', 0)
                                if isinstance(ov, dict): ov = ov.get('value', 0)
                                opp_score = int(ov) if ov else 0
                                break
                        pf += sv
                        pa += opp_score
                        if c.get('winner', False):
                            w += 1
                        else:
                            l += 1
        return w, l, pf, pa

    def get_team_data(team):
        abbr = TEAM_MAP.get(team, '')
        # Try current season from ESPN team endpoint
        url = f"{ESPN_SITE_V2}/teams/{abbr}"
        data = get_cached_or_fetch(url, f"team_{abbr}")
        rec = "0-0"
        pf = pa = 0
        if data and 'team' in data:
            for item in data['team']['record'].get('items', []):
                if item.get('type') == 'total':
                    rec = item.get('summary', '0-0')
                    for stat in item.get('stats', []):
                        if stat.get('name') == 'pointsFor': pf = int(stat.get('value', 0))
                        elif stat.get('name') == 'pointsAgainst': pa = int(stat.get('value', 0))
        w = l = 0
        if '-' in rec:
            try:
                w, l = map(int, rec.split('-'))
            except:
                pass
        # Current season has real games? Use it. Otherwise pull most recent completed games.
        if w + l == 0:
            # Try 2026 preseason first
            w, l, pf, pa = count_schedule(abbr, 2026, 1)
            # If nothing yet, try 2026 regular season
            if w + l == 0:
                w2, l2, pf2, pa2 = count_schedule(abbr, 2026, 2)
                if w2 + l2 > 0:
                    w, l, pf, pa = w2, l2, pf2, pa2
            # Fall back to 2025 regular season
            if w + l == 0:
                w, l, pf, pa = count_schedule(abbr, 2025, 2)
            rec = f"{w}-{l}"
        return {'record': rec, 'wins': w, 'losses': l, 'pf': pf, 'pa': pa}
    
    d1 = get_team_data(team1)
    d2 = get_team_data(team2)
    
    # Get weather
    w1 = get_weather_from_api(team1)
    w2 = get_weather_from_api(team2)
    
    # Get injuries
    inj1 = get_cached_or_fetch(f"{ESPN_SITE_V2}/injuries", "injuries")
    inj_count1 = inj_count2 = 0
    if inj1:
        for t in inj1.get('injuries', []):
            team_name = t.get('displayName', '').lower().strip()
            if team_name == team1.lower().strip():
                inj_count1 = len(t.get('injuries', []))
            elif team_name == team2.lower().strip():
                inj_count2 = len(t.get('injuries', []))
    # ESPN returns fake "25" for every team during offseason - detect and zero it
    # Only trust injury data if the two teams actually differ
    if inj_count1 == inj_count2 and inj_count1 >= 20:
        inj_count1 = inj_count2 = 0
    # Sanity cap
    inj_count1 = min(inj_count1, 15)
    inj_count2 = min(inj_count2, 15)
    
    # ---- WEIGHTED SCORING ----
    score1 = score2 = 0.0
    factors = []
    
    # 1. Win % (30%)
    total1 = d1['wins'] + d1['losses']
    total2 = d2['wins'] + d2['losses']
    wp1 = (d1['wins'] / total1) if total1 > 0 else 0.5
    wp2 = (d2['wins'] / total2) if total2 > 0 else 0.5
    # Weight by games played - teams with few games contribute less
    wp1_weight = min(total1 / 10.0, 1.0)
    wp2_weight = min(total2 / 10.0, 1.0)
    wp1 = 0.5 + (wp1 - 0.5) * wp1_weight
    wp2 = 0.5 + (wp2 - 0.5) * wp2_weight
    score1 += wp1 * 30
    score2 += wp2 * 30
    factors.append(f"Win%: {team1} {wp1:.0%} vs {team2} {wp2:.0%}")
    
    # 2. Point differential (20%)
    pd1 = d1['pf'] - d1['pa']
    pd2 = d2['pf'] - d2['pa']
    pd_norm = max(abs(pd1) + abs(pd2), 1)
    score1 += ((pd1 - pd2) / pd_norm + 1) * 10
    score2 += ((pd2 - pd1) / pd_norm + 1) * 10
    factors.append(f"Point diff: {team1} {pd1:+d} vs {team2} {pd2:+d}")
    
    # 3. Home field (15%) - team1 is home
    score1 += 15
    factors.append(f"Home field: {team1}")
    
    # 4. Injuries (10%)
    inj_impact1 = max(0, 10 - min(inj_count1, 10) * 0.8)
    inj_impact2 = max(0, 10 - min(inj_count2, 10) * 0.8)
    score1 += inj_impact1
    score2 += inj_impact2
    factors.append(f"Injuries: {team1} {inj_count1} out vs {team2} {inj_count2} out")
    
    # 5. Weather (5%)
    weather_impact1 = weather_impact2 = 5
    if w1 and w1.get('wind_speed', 0) > 15:
        weather_impact1 = 2
        factors.append(f"Weather: High wind at {team1} stadium")
    if w2 and w2.get('wind_speed', 0) > 15:
        weather_impact2 = 2
    score1 += weather_impact1
    score2 += weather_impact2
    
    # 6. Recent form - last 5 (15%)
    def get_recent_form(abbr):
        # Try 2026 regular season last 5
        for season, stype in [(2026, 2), (2026, 1), (2025, 2)]:
            sched_url = f"{ESPN_SITE_V2}/teams/{abbr}/schedule?season={season}&seasontype={stype}"
            sched = get_cached_or_fetch(sched_url, f"recent_{season}_{stype}_{abbr}")
            if not sched or 'events' not in sched:
                continue
            completed = []
            for ev in sched['events']:
                comp = ev.get('competitions', [{}])[0]
                if comp.get('status', {}).get('type', {}).get('completed'):
                    for c in comp.get('competitors', []):
                        if c.get('team', {}).get('abbreviation', '').upper() == abbr.upper():
                            completed.append(1 if c.get('winner', False) else 0)
                            break
            if completed:
                last5 = completed[-5:]
                return sum(last5) / len(last5)
        return 0.5
    
    rf1 = get_recent_form(t1_abbr)
    rf2 = get_recent_form(t2_abbr)
    score1 += rf1 * 15
    score2 += rf2 * 15
    factors.append(f"Recent form: {team1} {rf1:.0%} vs {team2} {rf2:.0%}")
    
    # 7. ML model boost (5%)
    if rf and gb:
        hs = strengths.get(t1_abbr, 0)
        as_ = strengths.get(t2_abbr, 0)
        diff = hs - as_
        feats = pd.DataFrame([[hs, as_, diff]], columns=['home_strength','away_strength','strength_diff'])
        ml_prob = (rf.predict_proba(feats)[0][1] + gb.predict_proba(feats)[0][1]) / 2
        score1 += ml_prob * 5
        score2 += (1 - ml_prob) * 5
        factors.append(f"ML model: {ml_prob:.0%} for {team1}")
    
    # Final probabilities
    total_score = score1 + score2
    prob1 = (score1 / total_score * 100) if total_score > 0 else 50.0
    prob2 = 100 - prob1
    
    # Confidence
    conf = abs(prob1 - prob2)
    level = "HIGH" if conf > 20 else "MEDIUM" if conf > 10 else "LOW"
    
    # Score prediction
    avg_offense1 = (d1['pf'] / total1) if total1 > 0 else 24
    avg_offense2 = (d2['pf'] / total2) if total2 > 0 else 21
    avg_defense1 = (d1['pa'] / total1) if total1 > 0 else 21
    avg_defense2 = (d2['pa'] / total2) if total2 > 0 else 24
    
    pred1 = (avg_offense1 + avg_defense2) / 2
    pred2 = (avg_offense2 + avg_defense1) / 2
    
    # Weather adjustment
    if w1 and w1.get('wind_speed', 0) > 15:
        pred1 -= 2
        pred2 -= 2
    if w1 and w1.get('precipitation', 0) > 50:
        pred1 -= 3
        pred2 -= 3
    
    # Home field bonus
    pred1 += 2
    
    # Injury adjustment (cap at 7 points)
    pred1 -= min(inj_count1, 5) * 1.5
    pred2 -= min(inj_count2, 5) * 1.5
    
    final1 = max(14, int(round(pred1)))
    final2 = max(14, int(round(pred2)))
    
    return jsonify({
        "team1": team1, "team2": team2,
        "team1_win_probability": round(prob1, 1),
        "team2_win_probability": round(prob2, 1),
        "predicted_winner": team1 if prob1 > prob2 else team2,
        "predicted_score": f"{final1}-{final2}",
        "confidence": round(conf, 1),
        "confidence_level": level,
        "key_factors": factors
    })
    

@app.route('/preseason_stats', methods=['GET'])
def get_preseason_stats():
    team = request.args.get('team', '')
    name = request.args.get('name', '')
    if not team or not name:
        return jsonify({'error': 'team and name required'}), 400
    try:
        for season in [2025, 2026]:
            for seasontype in [1, 2]:
                sched_url = f'{ESPN_SITE_V2}/teams/{TEAM_MAP.get(team, "")}/schedule?season={season}&seasontype={seasontype}'
                try:
                    sched = requests.get(sched_url, timeout=10).json()
                except:
                    continue
                for event in sched.get('events', []):
                    event_id = event.get('id', '')
                    if not event_id:
                        continue
                    try:
                        summary = requests.get(f'{ESPN_SITE_V2}/summary?event={event_id}', timeout=10).json()
                    except:
                        continue
                    if 'boxscore' not in summary:
                        continue
                    for team_data in summary['boxscore'].get('players', []):
                        for stat_group in team_data.get('statistics', []):
                            for athlete in stat_group.get('athletes', []):
                                athlete_name = athlete.get('athlete', {}).get('displayName', '')
                                if name.lower() in athlete_name.lower():
                                    stats = {}
                                    raw_stats = athlete.get('stats', [])
                                    if len(raw_stats) >= 2:
                                        if stat_group['name'] == 'passing':
                                            stats['completions'] = raw_stats[0].split('/')[0]
                                            stats['passing_attempts'] = raw_stats[0].split('/')[1]
                                            stats['passing_yards'] = int(raw_stats[1])
                                            if len(raw_stats) >= 5:
                                                stats['passing_tds'] = int(raw_stats[3])
                                        elif stat_group['name'] == 'rushing':
                                            stats['rushing_attempts'] = int(raw_stats[0]) if raw_stats[0].isdigit() else 0
                                            stats['rushing_yards'] = int(raw_stats[1])
                                            if len(raw_stats) >= 4:
                                                stats['rushing_tds'] = int(raw_stats[3])
                                        elif stat_group['name'] == 'receiving':
                                            stats['receptions'] = int(raw_stats[0]) if raw_stats[0].isdigit() else 0
                                            stats['receiving_yards'] = int(raw_stats[1])
                                            if len(raw_stats) >= 4:
                                                stats['receiving_tds'] = int(raw_stats[3])
                                    return jsonify({'stats': stats if stats else None})
        return jsonify({'stats': None})
    except Exception as e:
        return jsonify({'stats': None, 'error': str(e)})


@app.route('/live_scores', methods=['GET'])
def get_live_scores():
    url = f"{ESPN_SITE_V2}/scoreboard?dates=2026"
    try:
        response = requests.get(url, timeout=10)
        if response.status_code != 200:
            return jsonify({"scores": []})
        data = response.json()
        games = []
        for event in data.get('events', []):
            status = event.get('status', {}).get('type', {})
            state = status.get('description', 'Scheduled')
            detail = status.get('shortDetail', '')
            competitors = event.get('competitions', [{}])[0].get('competitors', [])
            if len(competitors) >= 2:
                away = competitors[0]
                home = competitors[1]
                games.append({
                    "away": away.get('team', {}).get('abbreviation', 'N/A'),
                    "home": home.get('team', {}).get('abbreviation', 'N/A'),
                    "away_score": int(away.get('score', 0)),
                    "home_score": int(home.get('score', 0)),
                    "status": state,
                    "detail": detail
                })
        live = [g for g in games if g['status'] in ['In Progress', 'Halftime']]
        return jsonify({"scores": live if live else games[:5]})
    except:
        return jsonify({"scores": []})

if __name__ == "__main__":
    print("NFL API Running on http://localhost:5000")
    app.run(host='0.0.0.0', port=5000, debug=True)