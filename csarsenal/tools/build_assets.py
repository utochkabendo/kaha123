"""Generate every asset of the mod into src/main/resources.

    python3 tools/build_assets.py            (from the csarsenal/ directory)
"""
import os
import sys
import json
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import meshlib
import weapons_rifles as R
import weapons_pistols as P
import character
import textures
import sounds
import fxtextures

ROOT = os.path.abspath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(ROOT, 'assets', 'csarsenal')

# item id -> (mesh builder, english name, russian name)
ITEMS = [
    ('glock', P.glock18, 'Glock-18', 'Glock-18'),
    ('usp_s', P.usp_s, 'USP-S', 'USP-S'),
    ('p2000', P.p2000, 'P2000', 'P2000'),
    ('p250', P.p250, 'P250', 'P250'),
    ('fiveseven', P.fiveseven, 'Five-SeveN', 'Five-SeveN'),
    ('tec9', P.tec9, 'Tec-9', 'Tec-9'),
    ('cz75a', P.cz75, 'CZ75-Auto', 'CZ75-Auto'),
    ('elite', P.beretta, 'Dual Berettas', 'Dual Berettas'),
    ('deagle', P.deagle, 'Desert Eagle', 'Desert Eagle'),
    ('revolver', P.revolver, 'R8 Revolver', 'Револьвер R8'),
    ('mac10', R.mac10, 'MAC-10', 'MAC-10'),
    ('mp9', R.mp9, 'MP9', 'MP9'),
    ('mp7', R.mp7, 'MP7', 'MP7'),
    ('mp5sd', R.mp5sd, 'MP5-SD', 'MP5-SD'),
    ('ump45', R.ump45, 'UMP-45', 'UMP-45'),
    ('p90', R.p90, 'P90', 'P90'),
    ('bizon', R.bizon, 'PP-Bizon', 'ПП-19 Бизон'),
    ('galilar', R.galil, 'Galil AR', 'Galil AR'),
    ('famas', R.famas, 'FAMAS', 'FAMAS'),
    ('ak47', R.ak47, 'AK-47', 'AK-47'),
    ('m4a4', R.m4a4, 'M4A4', 'M4A4'),
    ('m4a1_s', R.m4a1s, 'M4A1-S', 'M4A1-S'),
    ('sg556', R.sg553, 'SG 553', 'SG 553'),
    ('aug', R.aug, 'AUG', 'AUG'),
    ('ssg08', R.ssg08, 'SSG 08', 'SSG 08'),
    ('awp', R.awp, 'AWP', 'AWP'),
    ('g3sg1', R.g3sg1, 'G3SG1', 'G3SG1'),
    ('scar20', R.scar20, 'SCAR-20', 'SCAR-20'),
    ('nova', R.nova, 'Nova', 'Nova'),
    ('xm1014', R.xm1014, 'XM1014', 'XM1014'),
    ('sawedoff', R.sawedoff, 'Sawed-Off', 'Обрез'),
    ('mag7', R.mag7, 'MAG-7', 'MAG-7'),
    ('m249', R.m249, 'M249', 'M249'),
    ('negev', R.negev, 'Negev', 'Negev'),
    ('knife', P.knife, 'Knife', 'Нож'),
    ('knife_karambit', P.karambit, 'Karambit', 'Керамбит'),
    ('taser', P.zeus, 'Zeus x27', 'Zeus x27'),
    ('hegrenade', P.he, 'High Explosive Grenade', 'Осколочная граната'),
    ('flashbang', P.flashbang, 'Flashbang', 'Световая граната'),
    ('smokegrenade', P.smoke, 'Smoke Grenade', 'Дымовая граната'),
    ('molotov', P.molotov, 'Molotov', 'Коктейль Молотова'),
    ('incgrenade', P.incendiary, 'Incendiary Grenade', 'Зажигательная граната'),
    ('decoy', P.decoy, 'Decoy Grenade', 'Ложная граната'),
    ('c4', P.c4, 'C4 Explosive', 'Бомба C4'),
    ('kevlar', P.kevlar, 'Kevlar Vest', 'Бронежилет'),
    ('assaultsuit', P.helmet, 'Kevlar + Helmet', 'Бронежилет + шлем'),
    ('defuser', P.defuser, 'Defuse Kit', 'Набор сапёра'),
]

WEAPON_UV = {'wood': 0.22, 'wood_dark': 0.22, 'cloth': 0.12, 'tape': 0.05, 'c4_clay': 0.1}
AGENT_UV = {'shirt': 0.45, 'pants': 0.45, 'vest': 0.22, 'gear': 0.14, 'mask': 0.12, 'glove': 0.06, 'boot': 0.12, 'sole': 0.08,
            'belt': 0.08, 'helmet': 0.2, 'lens': 0.1, 'metal': 0.05}

LANG_EN = {
    'itemGroup.csarsenal': 'CS Arsenal',
    'key.categories.csarsenal': 'CS Arsenal',
    'key.csarsenal.reload': 'Reload',
    'key.csarsenal.inspect': 'Inspect weapon',
    'key.csarsenal.walk': 'Walk (silent)',
    'key.csarsenal.crouch': 'Crouch',
    'key.csarsenal.buy': 'Buy menu',
    'key.csarsenal.drop': 'Drop weapon',
    'death.attack.csarsenal.bullet': '%1$s was shot',
    'death.attack.csarsenal.bullet.player': '%1$s was shot by %2$s',
    'death.attack.csarsenal.bullet.item': '%1$s was shot by %2$s using %3$s',
    'death.attack.csarsenal.headshot': '%1$s was headshot',
    'death.attack.csarsenal.headshot.player': '%1$s was headshot by %2$s',
    'death.attack.csarsenal.headshot.item': '%1$s was headshot by %2$s using %3$s',
    'death.attack.csarsenal.knife': '%1$s was knifed',
    'death.attack.csarsenal.knife.player': '%1$s was knifed by %2$s',
    'death.attack.csarsenal.knife.item': '%1$s was knifed by %2$s',
    'death.attack.csarsenal.grenade': '%1$s was blown up',
    'death.attack.csarsenal.grenade.player': '%1$s was blown up by %2$s',
    'death.attack.csarsenal.grenade.item': '%1$s was blown up by %2$s',
    'death.attack.csarsenal.inferno': '%1$s burned to death',
    'death.attack.csarsenal.inferno.player': '%1$s was burned by %2$s',
    'death.attack.csarsenal.inferno.item': '%1$s was burned by %2$s',
    'death.attack.csarsenal.bomb': '%1$s was killed by the bomb',
    'death.attack.csarsenal.bomb.player': '%1$s was killed by the bomb planted by %2$s',
    'death.attack.csarsenal.bomb.item': '%1$s was killed by the bomb planted by %2$s',
    'death.attack.csarsenal.taser': '%1$s was tased',
    'death.attack.csarsenal.taser.player': '%1$s was tased by %2$s',
    'death.attack.csarsenal.taser.item': '%1$s was tased by %2$s',
    'csarsenal.hud.reloading': 'Reloading',
    'csarsenal.hud.planting': 'Planting the bomb...',
    'csarsenal.hud.defusing': 'Defusing...',
    'csarsenal.hud.bomb_planted': 'The bomb has been planted',
    'csarsenal.hud.bomb_defused': 'The bomb has been defused',
    'csarsenal.buy.title': 'Buy Menu',
    'csarsenal.buy.pistols': 'Pistols',
    'csarsenal.buy.heavy': 'Heavy',
    'csarsenal.buy.smgs': 'SMGs',
    'csarsenal.buy.rifles': 'Rifles',
    'csarsenal.buy.grenades': 'Grenades',
    'csarsenal.buy.gear': 'Gear',
    'csarsenal.buy.disabled': 'The buy menu is disabled on this server',
    'csarsenal.tooltip.damage': 'Damage: %s',
    'csarsenal.tooltip.armor_pen': 'Armor penetration: %s%%',
    'csarsenal.tooltip.rpm': 'Fire rate: %s RPM',
    'csarsenal.tooltip.magazine': 'Magazine: %s / %s',
    'csarsenal.tooltip.speed': 'Running speed: %s',
    'csarsenal.tooltip.price': 'Price: $%s',
    'csarsenal.team.set': 'You joined the %s',
    'csarsenal.team.t': 'Terrorists',
    'csarsenal.team.ct': 'Counter-Terrorists',
}
LANG_RU = {
    'itemGroup.csarsenal': 'CS Arsenal',
    'key.categories.csarsenal': 'CS Arsenal',
    'key.csarsenal.reload': 'Перезарядка',
    'key.csarsenal.inspect': 'Осмотр оружия',
    'key.csarsenal.walk': 'Шаг (бесшумно)',
    'key.csarsenal.crouch': 'Присесть',
    'key.csarsenal.buy': 'Меню закупки',
    'key.csarsenal.drop': 'Выбросить оружие',
    'death.attack.csarsenal.bullet': '%1$s застрелен',
    'death.attack.csarsenal.bullet.player': '%1$s застрелен игроком %2$s',
    'death.attack.csarsenal.bullet.item': '%1$s застрелен игроком %2$s из %3$s',
    'death.attack.csarsenal.headshot': '%1$s убит в голову',
    'death.attack.csarsenal.headshot.player': '%1$s убит в голову игроком %2$s',
    'death.attack.csarsenal.headshot.item': '%1$s убит в голову игроком %2$s из %3$s',
    'death.attack.csarsenal.knife': '%1$s зарезан',
    'death.attack.csarsenal.knife.player': '%1$s зарезан игроком %2$s',
    'death.attack.csarsenal.knife.item': '%1$s зарезан игроком %2$s',
    'death.attack.csarsenal.grenade': '%1$s подорвался',
    'death.attack.csarsenal.grenade.player': '%1$s подорван игроком %2$s',
    'death.attack.csarsenal.grenade.item': '%1$s подорван игроком %2$s',
    'death.attack.csarsenal.inferno': '%1$s сгорел',
    'death.attack.csarsenal.inferno.player': '%1$s сожжён игроком %2$s',
    'death.attack.csarsenal.inferno.item': '%1$s сожжён игроком %2$s',
    'death.attack.csarsenal.bomb': '%1$s погиб от взрыва бомбы',
    'death.attack.csarsenal.bomb.player': '%1$s погиб от бомбы игрока %2$s',
    'death.attack.csarsenal.bomb.item': '%1$s погиб от бомбы игрока %2$s',
    'death.attack.csarsenal.taser': '%1$s убит шокером',
    'death.attack.csarsenal.taser.player': '%1$s убит шокером игрока %2$s',
    'death.attack.csarsenal.taser.item': '%1$s убит шокером игрока %2$s',
    'csarsenal.hud.reloading': 'Перезарядка',
    'csarsenal.hud.planting': 'Установка бомбы...',
    'csarsenal.hud.defusing': 'Разминирование...',
    'csarsenal.hud.bomb_planted': 'Бомба заложена',
    'csarsenal.hud.bomb_defused': 'Бомба обезврежена',
    'csarsenal.buy.title': 'Меню закупки',
    'csarsenal.buy.pistols': 'Пистолеты',
    'csarsenal.buy.heavy': 'Тяжёлое',
    'csarsenal.buy.smgs': 'ПП',
    'csarsenal.buy.rifles': 'Винтовки',
    'csarsenal.buy.grenades': 'Гранаты',
    'csarsenal.buy.gear': 'Снаряжение',
    'csarsenal.buy.disabled': 'Меню закупки отключено на этом сервере',
    'csarsenal.tooltip.damage': 'Урон: %s',
    'csarsenal.tooltip.armor_pen': 'Бронепробитие: %s%%',
    'csarsenal.tooltip.rpm': 'Скорострельность: %s выстр/мин',
    'csarsenal.tooltip.magazine': 'Магазин: %s / %s',
    'csarsenal.tooltip.speed': 'Скорость бега: %s',
    'csarsenal.tooltip.price': 'Цена: $%s',
    'csarsenal.team.set': 'Вы вступили в команду: %s',
    'csarsenal.team.t': 'Террористы',
    'csarsenal.team.ct': 'Спецназ',
}


def main():
    t0 = time.time()
    os.makedirs(ASSETS, exist_ok=True)
    mesh_dir = os.path.join(ASSETS, 'meshes')
    os.makedirs(mesh_dir, exist_ok=True)
    total = 0
    for item_id, fn, en, ru in ITEMS:
        W = fn()
        W.name = item_id
        n = meshlib.export_model(W, f'{mesh_dir}/{item_id}.csm', f'{mesh_dir}/{item_id}.json', uv_scale=WEAPON_UV)
        total += n
    extra = []
    for fn in P.SHELLS:
        W = fn()
        meshlib.export_model(W, f'{mesh_dir}/{W.name}.csm', f'{mesh_dir}/{W.name}.json', uv_scale=WEAPON_UV)
        extra.append(W.name)
    print(f'meshes: {len(ITEMS)} items, {total} tris ({time.time() - t0:.1f}s)')
    ntex = len(textures.build_all(ROOT)) + fxtextures.build(ROOT)
    print(f'textures: {ntex}')
    ev = sounds.build_all(ROOT)
    print(f'sounds: {len(ev)} events ({time.time() - t0:.1f}s)')
    # item models
    mdir = os.path.join(ASSETS, 'models', 'item')
    os.makedirs(mdir, exist_ok=True)
    for item_id, fn, en, ru in ITEMS:
        with open(f'{mdir}/{item_id}.json', 'w') as fp:
            json.dump({'parent': 'minecraft:builtin/entity', 'gui_light': 'side',
                       'textures': {'particle': 'minecraft:item/iron_ingot'}}, fp)
    # lang
    ldir = os.path.join(ASSETS, 'lang')
    os.makedirs(ldir, exist_ok=True)
    en_map = dict(LANG_EN)
    ru_map = dict(LANG_RU)
    for item_id, fn, en, ru in ITEMS:
        en_map[f'item.csarsenal.{item_id}'] = en
        ru_map[f'item.csarsenal.{item_id}'] = ru
    for name, m in (('en_us', en_map), ('ru_ru', ru_map)):
        with open(f'{ldir}/{name}.json', 'w', encoding='utf-8') as fp:
            json.dump(m, fp, indent=1, ensure_ascii=False, sort_keys=True)
    with open(os.path.join(ASSETS, 'meshes', 'index.json'), 'w') as fp:
        json.dump([i[0] for i in ITEMS] + extra, fp)
    print(f'done in {time.time() - t0:.1f}s')


if __name__ == '__main__':
    main()
