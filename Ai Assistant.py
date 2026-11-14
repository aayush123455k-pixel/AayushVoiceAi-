"""
AI Voice & Task Assistant (Jarvis-style)
----------------------------------------
Yeh assistant aapki aawaz (voice) sunkar tasks execute karta hai:
1. YouTube par gaane/videos play karna
2. Google & Wikipedia par search karna
3. Computer ke apps (Notepad, Calculator, Chrome, etc.) open karna
4. Current time aur date batana
5. AI Brain (Google Gemini / OpenAI) ke sath connect karke kisi bhi sawal ka jawab dena
"""

import os
import sys
import datetime
import webbrowser
import subprocess

# Required external libraries check
try:
    import pyttsx3
except ImportError:
    pyttsx3 = None

try:
    import speech_recognition as sr
except ImportError:
    sr = None

try:
    import wikipedia
except ImportError:
    wikipedia = None

try:
    import pywhatkit
except ImportError:
    pywhatkit = None

# Optional AI LLM (Google Gemini) integration
try:
    import google.generativeai as genai
except ImportError:
    genai = None


# ----------------- Voice Engine Setup -----------------
def init_engine():
    if pyttsx3:
        try:
            engine = pyttsx3.init()
            voices = engine.getProperty('voices')
            # Select female or male voice if available
            if len(voices) > 1:
                engine.setProperty('voice', voices[1].id)
            engine.setProperty('rate', 175)  # Speaking speed
            return engine
        except Exception as e:
            print(f"[Warning] Audio engine initialize nahi ho paya: {e}")
    return None

engine = init_engine()

def speak(text):
    """Assistant speaks text and prints it on screen"""
    print(f"\n🤖 Assistant: {text}")
    if engine:
        try:
            engine.say(text)
            engine.runAndWait()
        except Exception:
            pass


def wish_user():
    """Wishes user according to current time"""
    hour = datetime.datetime.now().hour
    if 0 <= hour < 12:
        greeting = "Good Morning!"
    elif 12 <= hour < 18:
        greeting = "Good Afternoon!"
    else:
        greeting = "Good Evening!"
    
    speak(f"{greeting} Main aapka AI assistant hoon. Boliye, main aapke liye kya kar sakta hoon?")


# ----------------- Voice / Text Input -----------------
def take_command():
    """
    Mic se voice command leta hai.
    Agar microphone na ho ya error aaye to terminal se text input le leta hai.
    """
    if sr:
        recognizer = sr.Recognizer()
        try:
            with sr.Microphone() as source:
                print("\n🎙️ Sun raha hoon... (Listening...)")
                recognizer.adjust_for_ambient_noise(source, duration=0.8)
                recognizer.pause_threshold = 1.0
                audio = recognizer.listen(source, timeout=6, phrase_time_limit=8)

            print("⏳ Samajh raha hoon... (Recognizing...)")
            query = recognizer.recognize_google(audio, language='en-in')
            print(f"👤 Aapne kaha: {query}")
            return query.lower()
        except sr.WaitTimeoutError:
            print("Koi aawaz nahi suni gayi.")
            return ""
        except sr.UnknownValueError:
            speak("Maaf kijiye, samajh nahi aaya. Kripya dubara boliye.")
            return ""
        except Exception as e:
            print(f"Microphone issue ({e}). Keyboard input mode use kar rahe hain.")

    # Fallback if mic is not working or speech_recognition not installed
    query = input("\n⌨️ Type your command (ya 'exit' likhein): ")
    return query.lower()


# ----------------- AI Brain Query (Gemini LLM) -----------------
def ask_ai(prompt):
    """
    Generative AI se sawal ka smart answer leta hai.
    GEMINI_API_KEY environment variable set karein ya direct key dalein.
    """
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        return "Maine aapka sawal samjha, lekin AI brain activate karne ke liye GEMINI_API_KEY set karni hogi."
    
    if not genai:
        return "google-generativeai library install nahi hai. Run: pip install google-generativeai"

    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-1.5-flash")
        response = model.generate_content(prompt + " (Keep the answer short, natural and under 2 sentences)")
        return response.text.strip()
    except Exception as e:
        return f"AI response lane mein dikkat aayi: {e}"


# ----------------- Main Task Execution Loop -----------------
def execute_command(query):
    """Parses and executes user commands"""
    if not query:
        return True

    # 1. Exit / Stop
    if any(word in query for word in ["exit", "quit", "bye", "band ho jao", "stop"]):
        speak("Alvida! Aapka din shubh ho.")
        return False

    # 2. Time & Date
    elif "time" in query or "samay" in query:
        current_time = datetime.datetime.now().strftime("%I:%M %p")
        speak(f"Abhi samay hai: {current_time}")

    elif "date" in query or "tarikh" in query:
        current_date = datetime.datetime.now().strftime("%d %B %Y, %A")
        speak(f"Aaj ki tarikh hai: {current_date}")

    # 3. Wikipedia Search
    elif "wikipedia" in query:
        speak("Wikipedia par search kar raha hoon...")
        search_query = query.replace("wikipedia", "").strip()
        if wikipedia and search_query:
            try:
                results = wikipedia.summary(search_query, sentences=2)
                speak(f"Wikipedia ke anusar: {results}")
            except Exception:
                speak("Wikipedia par is vishay ki jankari nahi mili.")
        else:
            speak("Kya search karna hai, kripya vishay ka naam bataiye.")

    # 4. YouTube Video / Song Play
    elif "play" in query or "gana" in query or "song" in query:
        song = query.replace("play", "").replace("gana", "").replace("song", "").strip()
        if pywhatkit and song:
            speak(f"YouTube par {song} play kar raha hoon...")
            pywhatkit.playonyt(song)
        else:
            speak(f"YouTube open kar raha hoon...")
            webbrowser.open(f"https://www.youtube.com/results?search_query={song}")

    # 5. Open Popular Websites
    elif "open youtube" in query:
        speak("YouTube open kar raha hoon...")
        webbrowser.open("https://www.youtube.com")

    elif "open google" in query:
        speak("Google open kar raha hoon...")
        webbrowser.open("https://www.google.com")

    elif "open github" in query:
        speak("GitHub open kar raha hoon...")
        webbrowser.open("https://www.github.com")

    # 6. Google Search
    elif "search" in query or "google" in query:
        search_term = query.replace("search", "").replace("google", "").strip()
        if search_term:
            speak(f"Google par {search_term} dhoondh raha hoon...")
            webbrowser.open(f"https://www.google.com/search?q={search_term}")
        else:
            speak("Google par kya search karna hai, bataiye?")

    # 7. Desktop Applications (Windows/Linux/Mac)
    elif "open notepad" in query or "notepad" in query:
        speak("Notepad open kar raha hoon...")
        if sys.platform.startswith("win"):
            subprocess.Popen(["notepad.exe"])
        else:
            subprocess.Popen(["gedit"])

    elif "open calculator" in query or "calculator" in query:
        speak("Calculator khol raha hoon...")
        if sys.platform.startswith("win"):
            subprocess.Popen(["calc.exe"])
        elif sys.platform == "darwin":
            subprocess.Popen(["open", "-a", "Calculator"])
        else:
            subprocess.Popen(["gnome-calculator"])

    # 8. General Questions -> AI Brain (Gemini)
    else:
        speak("Iska jawab dhoondh raha hoon...")
        answer = ask_ai(query)
        speak(answer)

    return True


def main():
    print("=" * 60)
    print("        🚀 AI PERSONAL ASSISTANT START HO RAHA HAI...       ")
    print("=" * 60)
    wish_user()

    running = True
    while running:
        command = take_command()
        running = execute_command(command)


if __name__ == "__main__":
    main()