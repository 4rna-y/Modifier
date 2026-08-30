'use strict';

/**
 * E2E 用のヘッドレスクライアント。
 *
 * <p>26.2 のプロトコル (776) に対応した実装がまだ無いので、テストサーバーだけ 26.1 を使う。
 * プラグインの jar は本番と同じもの (26.2 でコンパイルしたもの) をそのまま載せる。
 *
 * 引数は JSON: {"port":25566,"version":"26.1","scenario":"selection"}
 * 結果は1行1件の JSON で標準出力へ流す。JUnit 側はそれを読んで検証する。
 * 観測を出すだけで、成否の判断はしない。
 */

const mineflayer = require('mineflayer');

const config = JSON.parse(process.argv[2]);
const HOST = config.host || '127.0.0.1';
const PORT = config.port;
const VERSION = config.version || '26.1';
const TIMEOUT_MS = config.timeoutMs || 120000;

/** 観測を1件流す。 */
function emit(event, fields) {
  process.stdout.write(JSON.stringify({ event, ...fields }) + '\n');
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** 参加してスポーンするまで待つ。リソースパックは常に受諾する。 */
function connect(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: HOST, port: PORT, username, auth: 'offline', version: VERSION,
    });
    bot.chatLog = [];
    bot.on('message', (message) => bot.chatLog.push(message.toString()));
    bot.on('resourcePack', () => {
      emit('resource_pack_offered', { bot: username });
      bot.acceptResourcePack();
    });
    bot.on('kicked', (reason) =>
      reject(new Error(username + ' kicked: ' + JSON.stringify(reason).slice(0, 300))));
    bot.on('error', reject);
    bot.once('spawn', () => {
      emit('spawned', { bot: username });
      resolve(bot);
    });
  });
}

/** 画面が開くまで待つ。すでに開いていればそれを返す。 */
function waitWindow(bot, timeoutMs = 20000) {
  if (bot.currentWindow) {
    return Promise.resolve(bot.currentWindow);
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('画面が開かない')), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

/**
 * 特定の語を含むチャットを1件だけ取り出す。
 *
 * 配列のまま出すと、JUnit 側の素朴な取り出しが "[Modifier]" の "]" で切れてしまう。
 * 文字列1件にしておく。
 */
function firstMatch(bot, fragment) {
  return bot.chatLog.find((line) => line.includes(fragment)) || '';
}

/**
 * HP が動くまで待つ。動かないまま時間切れなら null。
 *
 * <p>ダメージはサーバー側のコンソールから与えるので、届いたことをこれで知る。
 */
function waitHealth(bot, timeoutMs) {
  const before = bot.health;
  return new Promise((resolve) => {
    const onHealth = () => {
      if (bot.health !== before) {
        bot.removeListener('health', onHealth);
        resolve(bot.health);
      }
    };
    bot.on('health', onHealth);
    setTimeout(() => {
      bot.removeListener('health', onHealth);
      resolve(null);
    }, timeoutMs);
  });
}

/** 開いている画面の中身を「スロット番号 → アイテム名」で書き出す。 */
function slotsOf(window) {
  const slots = {};
  window.slots.forEach((slot, index) => {
    if (slot) {
      slots[index] = slot.name;
    }
  });
  return slots;
}

function titleOf(window) {
  // タイトルは NBT の複合型で来るので、text を掘り出す
  const raw = window.title;
  if (typeof raw === 'string') {
    return raw;
  }
  return raw?.value?.text?.value ?? JSON.stringify(raw);
}

// ---- シナリオ -------------------------------------------------------------

const SCENARIOS = {
  /** 参加からモディファイア確定までの一周。 */
  async selection() {
    const bot = await connect('E2eSelector');

    const opened = await waitWindow(bot);
    emit('gui_opened', {
      bot: 'E2eSelector', when: 'join',
      title: titleOf(opened), slots: slotsOf(opened),
    });

    // 未選択のまま閉じたら開き直すはず
    bot.closeWindow(opened);
    await sleep(2000);
    const reopened = await waitWindow(bot, 10000);
    emit('gui_reopened', { bot: 'E2eSelector', slots: slotsOf(reopened) });

    // 中央左のスロットを選ぶ
    const chosen = reopened.slots[11];
    await bot.clickWindow(11, 0, 0);
    await sleep(2000);
    emit('choice_clicked', {
      bot: 'E2eSelector', slot: 11, item: chosen ? chosen.name : null,
      windowClosed: !bot.currentWindow,
      confirmMessage: firstMatch(bot, "選びました"),
    });

    // 確定後は開き直さないはず
    await sleep(3000);
    emit('after_choice', { bot: 'E2eSelector', windowOpen: !!bot.currentWindow });

    bot.quit();
  },

  /**
   * 選んだ効果が実際に乗るか。
   *
   * <p>デブを選んでから固定量のダメージを受け、HP の減り方を見る。
   * ダメージはサーバー側のコンソールから /damage で与える (JUnit 側が送る)。
   */
  async effect_fat() {
    const bot = await connect('E2eFatty');
    let window = await waitWindow(bot);

    // 3択は16種からの抽選なので、狙ったものが出るまで引き直す。
    // /modifier select は選択を捨てて引き直すので、これで狙い撃ちできる。
    let slot = null;
    for (let attempt = 0; attempt < 30 && !slot; attempt++) {
      slot = Object.entries(slotsOf(window)).find(([, name]) => name === 'bread');
      if (slot) {
        break;
      }
      bot.closeWindow(window);
      await sleep(400);
      bot.chat('/modifier select');
      await sleep(1200);
      window = await waitWindow(bot, 10000);
    }
    if (!slot) {
      emit('choice_missing', { wanted: 'bread', slots: slotsOf(window) });
      bot.quit();
      return;
    }
    emit('rolled_for_choice', { wanted: 'bread', slot: Number(slot[0]) });
    await bot.clickWindow(Number(slot[0]), 0, 0);
    await sleep(2000);
    emit('choice_clicked', {
      bot: 'E2eFatty', slot: Number(slot[0]), item: 'bread',
      confirmMessage: firstMatch(bot, '選びました'),
    });

    // JUnit 側がダメージを与えるのを待つ。回復に埋もれないよう、
    // 最初に HP が下がった瞬間の値をそのまま報告する。
    const before = bot.health;
    const hit = new Promise((resolve) => {
      const onHealth = () => {
        if (bot.health < before) {
          bot.removeListener('health', onHealth);
          resolve(bot.health);
        }
      };
      bot.on('health', onHealth);
      setTimeout(() => resolve(null), 15000);
    });
    emit('ready_for_damage', { bot: 'E2eFatty', health: before });

    const after = await hit;
    emit('health_after_damage', {
      bot: 'E2eFatty', before, after,
      lost: after === null ? -1 : before - after,
    });
    bot.quit();
  },

  /**
   * 選択済みなら、入り直しても画面が出ないこと。
   * 併せて /modifier select で選び直せることも見る。
   */
  async rejoin() {
    const first = await connect('E2eRejoin');
    const window = await waitWindow(first);
    await first.clickWindow(11, 0, 0);
    await sleep(2000);
    emit('choice_clicked', {
      bot: 'E2eRejoin', slot: 11,
      confirmMessage: firstMatch(first, '選びました'),
    });
    first.quit();
    await sleep(3000);

    // 入り直し。選択済みなので画面は出ないはず
    const again = await connect('E2eRejoin');
    let reopened = false;
    again.on('windowOpen', () => { reopened = true; });
    await sleep(8000);
    emit('rejoined', { bot: 'E2eRejoin', guiOpened: reopened });

    // 選び直しは明示的に頼んだときだけ
    again.chat('/modifier select');
    await sleep(3000);
    emit('after_reselect_command', {
      bot: 'E2eRejoin', guiOpen: !!again.currentWindow,
    });
    again.quit();
  },

  /**
   * 狙ったモディファイアを引き当てて確定する。
   *
   * <p>3択は16種からの重み付き抽選なので、/modifier select で引き直す。要 OP。
   *
   * <p>試行回数は「一番軽い冷笑 (重み3、出現率 約9.5%) でも取りこぼさない」を目安に取る。
   * 60回なら空振りする確率は 0.3% ほど。
   */
  async pick(bot, itemName) {
    let window = await waitWindow(bot);
    for (let attempt = 0; attempt < 60; attempt++) {
      const slot = Object.entries(slotsOf(window)).find(([, n]) => n === itemName);
      if (slot) {
        await bot.clickWindow(Number(slot[0]), 0, 0);
        await sleep(1500);
        return true;
      }
      bot.closeWindow(window);
      await sleep(400);
      bot.chat('/modifier select');
      await sleep(1200);
      window = await waitWindow(bot, 10000);
    }
    return false;
  },

  /**
   * シールドバッシュ。
   *
   * <p>盾を構えた側が殴られたとき、攻撃した側へ反射が返るか。
   * DamageModifier.BLOCKING が実ゲームで値を持つかの確認でもある。
   */
  async effect_shield_bash() {
    const defender = await connect('E2eDefender');
    const attacker = await connect('E2eAttacker');

    if (!await SCENARIOS.pick(defender, 'shield')) {
      emit('choice_missing', { wanted: 'shield' });
      defender.quit(); attacker.quit();
      return;
    }
    // 攻撃側は素の状態にしておく (効果の混線を避ける)
    await SCENARIOS.pick(attacker, 'bread');
    emit('both_ready', {});

    // JUnit 側が盾と剣を配るのを待ち、防御側は自分で盾を構える
    await sleep(4000);
    const shield = defender.inventory.items().find((i) => i.name === 'shield');
    if (!shield) {
      emit('failed', { reason: '盾を受け取れていない' });
      defender.quit(); attacker.quit();
      return;
    }
    await defender.equip(shield, 'off-hand');
    defender.activateItem(true);          // オフハンド = 盾を構える
    await sleep(1000);
    emit('shield_raised', {});

    const attackerBefore = attacker.health;
    emit('ready_for_attack', { attackerHealth: attackerBefore });

    // 攻撃側が防御側を殴る
    const target = attacker.players[defender.username]?.entity
      ?? Object.values(attacker.entities).find((e) => e.username === defender.username);
    if (!target) {
      emit('failed', { reason: '相手を見つけられない' });
      defender.quit(); attacker.quit();
      return;
    }
    for (let i = 0; i < 3; i++) {
      attacker.attack(target);
      await sleep(700);
    }
    await sleep(1500);
    emit('attacker_health', {
      before: attackerBefore, after: attacker.health,
      lost: attackerBefore - attacker.health,
    });
    defender.quit(); attacker.quit();
  },

  /**
   * 二段ジャンプ。
   *
   * <p>空中で飛行を切り替えると、サーバーが打ち消して上向きの速度を与えるはず。
   * mineflayer から飛行トグルのパケットを直接送って確かめる。
   */
  async effect_double_jump() {
    const bot = await connect('E2eJumper');
    if (!await SCENARIOS.pick(bot, 'leather_boots')) {
      emit('choice_missing', { wanted: 'leather_boots' });
      bot.quit();
      return;
    }
    await sleep(2000);

    // 1回目のジャンプで浮く
    bot.setControlState('jump', true);
    await sleep(300);
    bot.setControlState('jump', false);
    await sleep(300);

    // 速度はクライアント側の予測で濁るので、実際に登った高さで見る
    const beforeY = bot.entity.position.y;
    // 空中で飛行トグル (クライアントが「飛ぼうとした」と申告する)
    bot._client.write('abilities', { flags: 2 });   // serverbound は flags のみ

    let peak = beforeY;
    for (let i = 0; i < 20; i++) {
      await sleep(100);
      peak = Math.max(peak, bot.entity.position.y);
    }
    emit('double_jump', { beforeY, peakY: peak, climbed: peak - beforeY });
    bot.quit();
  },

  /**
   * スウィフトネスブーツと落下ダメージ。
   *
   * <p>二段ジャンプは飛行許可フラグを借りて実装している。バニラは「飛行を許された
   * プレイヤーは落下ダメージを受けない」ので、素のままだと落下ダメージが丸ごと
   * 消えてしまう。二段ジャンプを使わずに落ちて、ちゃんと痛いかを見る。
   *
   * <p>高いところへは JUnit 側が tp で運ぶ。
   */
  async effect_fall_damage() {
    const bot = await connect('E2eFaller');
    if (!await SCENARIOS.pick(bot, 'leather_boots')) {
      emit('choice_missing', { wanted: 'leather_boots' });
      bot.quit();
      return;
    }
    await sleep(2000);

    const before = bot.health;
    const groundY = bot.entity.position.y;
    // 二段ジャンプは使わない。落ちるだけ
    emit('ready_to_fall', { health: before, y: groundY });

    const landed = waitHealth(bot, 20000);
    const after = await landed;
    // 落ちきってから読む
    await sleep(2000);
    emit('fall_damage', {
      before, after: after === null ? bot.health : after,
      lost: after === null ? 0 : before - after,
      y: bot.entity.position.y, groundY,
    });
    bot.quit();
  },

  /**
   * 不眠症。
   *
   * <p>ベッドで夜を明かしたとき、TimeSkipEvent の時点で寝ている判定になるか。
   * JUnit 側が夜にしてベッドを置くので、ボットは寝るだけ。
   */
  async effect_insomnia() {
    const bot = await connect('E2eSleeper');
    if (!await SCENARIOS.pick(bot, 'red_bed')) {
      emit('choice_missing', { wanted: 'red_bed' });
      bot.quit();
      return;
    }
    emit('ready_to_sleep', {});
    // JUnit 側がベッドを置いて夜にするのを待つ
    await sleep(5000);

    const bed = bot.findBlock({
      matching: (block) => block.name.endsWith('_bed'), maxDistance: 8,
    });
    if (!bed) {
      emit('no_bed', {});
      bot.quit();
      return;
    }
    try {
      await bot.sleep(bed);
      emit('sleeping', {});
    } catch (error) {
      emit('sleep_failed', { reason: error.message });
      bot.quit();
      return;
    }
    // 夜が明けるのを待ち、効果が付いたか見る
    await sleep(8000);
    emit('effects_after_waking', {
      effects: Object.keys(bot.entity.effects || {}).join(','),
      count: Object.keys(bot.entity.effects || {}).length,
    });
    bot.quit();
  },

  /**
   * 死亡とワールドリセットの噛み合わせ。
   *
   * <p>Modifier は致死ダメージを EntityDamageEvent で打ち消すので、打ち消した死では
   * PlayerDeathEvent が飛ばない = wiah のリセットも走らない。冷笑は一度だけ死を
   * 打ち消すため、1回目と2回目で結果が変わるはず。それを1人で通しで見る。
   *
   * <p>ダメージは JUnit 側が /damage で与える。判断はせず、観測だけ流す。
   */
  async death_reset() {
    const bot = await connect('E2eDoomed');
    // 冷笑の土台アイテムは不死のトーテム
    if (!await SCENARIOS.pick(bot, 'totem_of_undying')) {
      emit('choice_missing', { wanted: 'totem_of_undying' });
      bot.quit();
      return;
    }

    let died = false;
    let kicked = null;
    bot.on('death', () => { died = true; });
    bot.on('kicked', (reason) => { kicked = JSON.stringify(reason).slice(0, 300); });
    await sleep(1500);

    // ---- 1回目。冷笑が打ち消して HP 半分で立ち上がるはず
    emit('ready_for_first_death', { health: bot.health });
    // 蘇生の値は「最初に HP が動いた瞬間」で見る。後から読むと、
    // 自然回復が効いている環境では上に持ち上げられてしまう。
    const revived = await waitHealth(bot, 15000);
    // 蘇生後のテレポートが落ち着くまで置く
    await sleep(4000);
    emit('after_first_death', {
      health: revived, healthLater: bot.health, died, kicked: kicked !== null,
      message: firstMatch(bot, '死ぬわけがない'),
    });

    // ---- 2回目。チャージを使い切っているので本当に死ぬ
    died = false;
    emit('ready_for_second_death', { health: bot.health });
    const kickedOff = new Promise((resolve) => {
      bot.once('kicked', () => resolve(true));
      bot.once('end', () => resolve(true));
      setTimeout(() => resolve(false), 25000);
    });
    await kickedOff;
    emit('after_second_death', { died, kicked: kicked !== null, reason: kicked });

    try {
      bot.quit();
    } catch (error) {
      // すでに切れている。リセットで蹴られた後なので想定内
    }
  },

  /** 複数人が同時に居る状態を作る。 */
  async multiplayer() {
    const names = ['E2eAlpha', 'E2eBravo', 'E2eCharlie'];
    const bots = [];
    for (const name of names) {
      const bot = await connect(name);
      const window = await waitWindow(bot);
      emit('gui_opened', { bot: name, when: 'join', slots: slotsOf(window) });
      await bot.clickWindow(13, 0, 0);
      await sleep(1500);
      emit('choice_clicked', {
        bot: name, slot: 13,
        confirmMessage: firstMatch(bot, "選びました"),
      });
      bots.push(bot);
    }
    emit('all_joined', { count: bots.length, names });
    await sleep(1000);
    bots.forEach((bot) => bot.quit());
  },
};

(async () => {
  const scenario = SCENARIOS[config.scenario];
  if (!scenario) {
    emit('failed', { reason: '知らないシナリオ: ' + config.scenario });
    process.exit(2);
  }
  const guard = setTimeout(() => {
    emit('failed', { reason: 'シナリオがタイムアウトした' });
    process.exit(3);
  }, TIMEOUT_MS);

  try {
    await scenario();
    emit('done', {});
    clearTimeout(guard);
    await sleep(300);
    process.exit(0);
  } catch (error) {
    emit('failed', { reason: error.message });
    clearTimeout(guard);
    await sleep(300);
    process.exit(1);
  }
})();
