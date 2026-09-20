from __future__ import annotations

from dataclasses import dataclass, asdict
import random
from typing import List

FRUITS = ["banana", "apple", "orange", "strawberry", "pear", "grape"]

@dataclass
class Scene:
    speaker: str
    line: str
    emotion: str
    background: str

@dataclass
class Story:
    language: str
    title: str
    hook: str
    characters: List[str]
    scenes: List[Scene]
    caption: str
    hashtags: List[str]

    def to_dict(self):
        data = asdict(self)
        return data

EN_THEMES = {
    "surprise": [
        ("The Golden Paint", [
            ("banana", "Wait... why is that bucket glowing?", "curious", "cozy_room"),
            ("apple", "Because I found GOLD paint! One drop and anything looks rich!", "excited", "cozy_room"),
            ("banana", "Please don't pour it on me.", "worried", "cozy_room"),
            ("orange", "Too late! I only need one tiny splash!", "mischievous", "cozy_room"),
            ("banana", "Why are my shoes sparkling?!", "shocked", "cozy_room"),
            ("apple", "Congratulations. You're officially premium fruit.", "happy", "cozy_room"),
        ]),
        ("The Secret Door", [
            ("pear", "Did that wall just blink at me?", "curious", "kitchen"),
            ("strawberry", "It's not a wall. It's a secret door.", "excited", "kitchen"),
            ("pear", "Secret doors never lead to normal things.", "worried", "kitchen"),
            ("strawberry", "Exactly. That's why we're opening it.", "mischievous", "kitchen"),
            ("orange", "Surprise! Welcome to the snack kingdom!", "happy", "candy_world"),
            ("pear", "Okay... I take back everything I said.", "happy", "candy_world"),
        ]),
    ],
    "funny": [
        ("Banana's Big Interview", [
            ("banana", "Today I finally get my dream job.", "confident", "office"),
            ("apple", "What's your strongest skill?", "serious", "office"),
            ("banana", "I can stay calm under pressure.", "confident", "office"),
            ("orange", "There's a spider behind you.", "mischievous", "office"),
            ("banana", "AAAAH! I resign!", "shocked", "office"),
            ("apple", "Interview complete.", "happy", "office"),
        ]),
    ],
}

AR_THEMES = {
    "surprise": [
        ("الدهان الذهبي", [
            ("banana", "استنى... ليه الجردل ده بينور؟", "curious", "cozy_room"),
            ("apple", "علشان لقيت دهان دهبي! نقطة واحدة وكل حاجة تبان فخمة!", "excited", "cozy_room"),
            ("banana", "بس أوعى تصبه عليا.", "worried", "cozy_room"),
            ("orange", "متأخر! أنا محتاج رشة صغيرة بس!", "mischievous", "cozy_room"),
            ("banana", "ليه الجزمة بتاعتي بتلمع؟!", "shocked", "cozy_room"),
            ("apple", "مبروك. بقيت فاكهة بريميوم رسميًا.", "happy", "cozy_room"),
        ]),
        ("الباب السري", [
            ("pear", "هو الحيط ده غمزلي؟", "curious", "kitchen"),
            ("strawberry", "ده مش حيط. ده باب سري.", "excited", "kitchen"),
            ("pear", "الأبواب السرية عمرها ما بتودّي لحاجة عادية.", "worried", "kitchen"),
            ("strawberry", "بالظبط. عشان كده هنفتحه.", "mischievous", "kitchen"),
            ("orange", "مفاجأة! أهلاً بيكم في مملكة السناكس!", "happy", "candy_world"),
            ("pear", "تمام... أنا بسحب كل كلامي.", "happy", "candy_world"),
        ]),
    ],
    "funny": [
        ("مقابلة شغل الموزة", [
            ("banana", "النهارده أخيرًا هاخد شغلانة أحلامي.", "confident", "office"),
            ("apple", "إيه أقوى مهارة عندك؟", "serious", "office"),
            ("banana", "بعرف أفضّل هادي تحت الضغط.", "confident", "office"),
            ("orange", "في عنكبوت وراك.", "mischievous", "office"),
            ("banana", "آااه! أنا مستقيل!", "shocked", "office"),
            ("apple", "المقابلة خلصت.", "happy", "office"),
        ]),
    ],
}


def generate_story(language: str = "en", theme: str = "surprise", seed: int | None = None) -> Story:
    rng = random.Random(seed)
    lang = "ar" if language.lower().startswith("ar") else "en"
    bank = AR_THEMES if lang == "ar" else EN_THEMES
    choices = bank.get(theme, bank["surprise"])
    title, raw_scenes = rng.choice(choices)
    scenes = [Scene(*row) for row in raw_scenes]
    characters = list(dict.fromkeys(s.speaker for s in scenes))
    if lang == "ar":
        hook = scenes[0].line
        caption = f"{title} 😂🍌 النهاية غير متوقعة!"
        tags = ["#قصص", "#فواكه", "#شورتس", "#تيك_توك", "#ذكاء_اصطناعي"]
    else:
        hook = scenes[0].line
        caption = f"{title} 😂🍌 Wait for the ending!"
        tags = ["#shorts", "#tiktok", "#funny", "#fruit", "#storytime"]
    return Story(lang, title, hook, characters, scenes, caption, tags)
