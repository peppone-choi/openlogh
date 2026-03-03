export const LEGACY = {
  baseUrl: "http://localhost:8888",
  gameUrl: "http://localhost:8888/sam/che/",
  credentials: { username: "admin", password: "admin123" },
};

export const NEW_SYSTEM = {
  baseUrl: "http://localhost:80",
  apiUrl: "http://localhost:8080/api",
  gameAppUrl: "http://localhost:9001",
  credentials: { loginId: "admin", password: "admin123" },
};

export interface PageRouteItem {
  name: string;
  legacy: string;
  route: string;
  markers: readonly string[];
}

export const PAGE_ROUTES: PageRouteItem[] = [
  {
    name: "Main",
    legacy: "/sam/che/",
    route: "/",
    markers: ["현재:", "장수 동향", "국가방침"],
  },
  {
    name: "Map",
    legacy: "/sam/che/",
    route: "/map",
    markers: ["지도", "도시", "세력"],
  },
  {
    name: "Commands",
    legacy: "/sam/che/",
    route: "/commands",
    markers: ["명령", "예약", "턴"],
  },
  {
    name: "City",
    legacy: "/sam/che/b_currentCity.php",
    route: "/city",
    markers: ["도시", "인구", "농업"],
  },
  {
    name: "Nation",
    legacy: "/sam/che/b_myKingdomInfo.php",
    route: "/nation",
    markers: ["세력", "국력", "군주"],
  },
  {
    name: "General",
    legacy: "/sam/che/",
    route: "/general",
    markers: ["내 장수", "통솔", "무력"],
  },
  {
    name: "Board",
    legacy: "/sam/che/v_board.php",
    route: "/board",
    markers: ["게시판", "글", "댓글"],
  },
  {
    name: "Troop",
    legacy: "/sam/che/v_troop.php",
    route: "/troop",
    markers: ["부대", "장수", "편성"],
  },
  {
    name: "Diplomacy",
    legacy: "/sam/che/t_diplomacy.php",
    route: "/diplomacy",
    markers: ["외교", "제의", "국가"],
  },
  {
    name: "Superior",
    legacy: "/sam/che/b_myBossInfo.php",
    route: "/superior",
    markers: ["상급", "관직", "권한"],
  },
  {
    name: "Internal Affairs",
    legacy: "/sam/che/v_nationStratFinan.php",
    route: "/internal-affairs",
    markers: ["내무", "정책", "재정"],
  },
  {
    name: "Chief",
    legacy: "/sam/che/v_chiefCenter.php",
    route: "/chief",
    markers: ["사령", "명령", "군단"],
  },
  {
    name: "NPC Control",
    legacy: "/sam/che/v_NPCControl.php",
    route: "/npc-control",
    markers: ["NPC", "정책", "우선순위"],
  },
  {
    name: "Generals",
    legacy: "/sam/che/b_genList.php",
    route: "/generals",
    markers: ["장수", "이름", "능력"],
  },
  {
    name: "Tournament",
    legacy: "/sam/che/b_tournament.php",
    route: "/tournament",
    markers: ["토너먼트", "참가", "경기"],
  },
  {
    name: "Nation Cities",
    legacy: "/sam/che/b_myCityInfo.php",
    route: "/nation-cities",
    markers: ["세력도시", "도시", "인구"],
  },
  {
    name: "Nation Generals",
    legacy: "/sam/che/v_nationGeneral.php",
    route: "/nation-generals",
    markers: ["세력장수", "장수", "관직"],
  },
  {
    name: "Nations",
    legacy: "/sam/che/v_globalDiplomacy.php",
    route: "/nations",
    markers: ["세력일람", "국가", "외교"],
  },
  {
    name: "Battle Center",
    legacy: "/sam/che/v_battleCenter.php",
    route: "/battle-center",
    markers: ["전투", "기록", "로그"],
  },
  {
    name: "Inherit",
    legacy: "/sam/che/v_inheritPoint.php",
    route: "/inherit",
    markers: ["유산", "포인트", "강화"],
  },
  {
    name: "My Page",
    legacy: "/sam/che/b_myPage.php",
    route: "/my-page",
    markers: ["설정", "계정", "내정보"],
  },
  {
    name: "Auction",
    legacy: "/sam/che/v_auction.php",
    route: "/auction",
    markers: ["경매", "입찰", "거래"],
  },
  {
    name: "Betting",
    legacy: "/sam/che/b_betting.php",
    route: "/betting",
    markers: ["베팅", "배당", "예측"],
  },
  {
    name: "History",
    legacy: "/sam/che/v_history.php",
    route: "/history",
    markers: ["연감", "기록", "연도"],
  },
  {
    name: "Vote",
    legacy: "/sam/che/v_vote.php",
    route: "/vote",
    markers: ["투표", "설문", "선택"],
  },
  {
    name: "Best Generals",
    legacy: "/sam/che/a_bestGeneral.php",
    route: "/best-generals",
    markers: ["명장", "랭킹", "능력"],
  },
  {
    name: "Emperor",
    legacy: "/sam/che/a_emperior.php",
    route: "/emperor",
    markers: ["황제", "천하", "조정"],
  },
  {
    name: "Hall of Fame",
    legacy: "/sam/che/a_hallOfFame.php",
    route: "/hall-of-fame",
    markers: ["명예의전당", "시즌", "기록"],
  },
  {
    name: "NPC List",
    legacy: "/sam/che/a_npcList.php",
    route: "/npc-list",
    markers: ["NPC", "일람", "장수"],
  },
  {
    name: "Traffic",
    legacy: "/sam/che/a_traffic.php",
    route: "/traffic",
    markers: ["접속", "현황", "트래픽"],
  },
  {
    name: "Messages",
    legacy: "/sam/che/",
    route: "/messages",
    markers: ["서신", "개인", "국가"],
  },
  {
    name: "Spy",
    legacy: "/sam/che/",
    route: "/spy",
    markers: ["암행", "정보", "첩보"],
  },
  {
    name: "Personnel",
    legacy: "/sam/che/",
    route: "/personnel",
    markers: ["인사", "관직", "임명"],
  },
  {
    name: "Processing",
    legacy: "/sam/che/",
    route: "/processing",
    markers: ["처리", "명령", "턴"],
  },
  {
    name: "Battle Simulator",
    legacy: "/sam/che/",
    route: "/battle-simulator",
    markers: ["시뮬", "전투", "계산"],
  },
  {
    name: "Battle",
    legacy: "/sam/che/",
    route: "/battle",
    markers: ["감찰", "전투", "로그"],
  },
  {
    name: "Dynasty",
    legacy: "/sam/che/",
    route: "/dynasty",
    markers: ["왕조", "계보", "가문"],
  },
];

export interface CommandItem {
  actionCode: string;
  category: string;
  safeToExecute: boolean;
  effectChecks: readonly ("city" | "general" | "nation")[];
}

export const GENERAL_COMMANDS: CommandItem[] = [
  {
    actionCode: "휴식",
    category: "default",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "농지개간",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["city"],
  },
  {
    actionCode: "상업투자",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["city"],
  },
  {
    actionCode: "치안강화",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["city"],
  },
  {
    actionCode: "수비강화",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["city"],
  },
  {
    actionCode: "성벽보수",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["city"],
  },
  {
    actionCode: "정착장려",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["city"],
  },
  {
    actionCode: "주민선정",
    category: "civil",
    safeToExecute: false,
    effectChecks: ["city"],
  },
  {
    actionCode: "기술연구",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "모병",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general", "city"],
  },
  {
    actionCode: "징병",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general", "city"],
  },
  {
    actionCode: "훈련",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "사기진작",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "소집해제",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "숙련전환",
    category: "civil",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "물자조달",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general", "city"],
  },
  {
    actionCode: "군량매매",
    category: "civil",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "헌납",
    category: "civil",
    safeToExecute: true,
    effectChecks: ["general", "nation"],
  },
  {
    actionCode: "단련",
    category: "personal",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "출병",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general", "city"],
  },
  {
    actionCode: "이동",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "집합",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "귀환",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "접경귀환",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "강행",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "거병",
    category: "military",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "전투태세",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "화계",
    category: "military",
    safeToExecute: false,
    effectChecks: ["city"],
  },
  {
    actionCode: "첩보",
    category: "military",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "선동",
    category: "military",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "탈취",
    category: "military",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "파괴",
    category: "military",
    safeToExecute: false,
    effectChecks: ["city"],
  },
  {
    actionCode: "요양",
    category: "military",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "방랑",
    category: "military",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "등용",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "등용수락",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "임관",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "랜덤임관",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "장수대상임관",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "하야",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "은퇴",
    category: "political",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "건국",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "무작위건국",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "모반시도",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "선양",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "해산",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "견문",
    category: "political",
    safeToExecute: true,
    effectChecks: ["general"],
  },
  {
    actionCode: "인재탐색",
    category: "political",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "증여",
    category: "political",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "장비매매",
    category: "political",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "내정특기초기화",
    category: "political",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "전투특기초기화",
    category: "political",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "NPC능동",
    category: "special",
    safeToExecute: false,
    effectChecks: ["general"],
  },
  {
    actionCode: "CR건국",
    category: "special",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "CR맹훈련",
    category: "special",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
];

export const NATION_COMMANDS: CommandItem[] = [
  {
    actionCode: "Nation휴식",
    category: "default",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "포상",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "몰수",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "감축",
    category: "resource",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "증축",
    category: "resource",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "발령",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "천도",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "백성동원",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "물자원조",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "국기변경",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "국호변경",
    category: "resource",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "선전포고",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "종전제의",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "종전수락",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "불가침제의",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "불가침수락",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "불가침파기제의",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "불가침파기수락",
    category: "diplomacy",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "급습",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "수몰",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "허보",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "초토화",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "필사즉생",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "이호경식",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "피장파장",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "의병모집",
    category: "strategic",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "극병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "대검병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "무희연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "산저병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "상병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "원융노병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "음귀병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "화륜차연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "화시병연구",
    category: "research",
    safeToExecute: true,
    effectChecks: ["nation"],
  },
  {
    actionCode: "무작위수도이전",
    category: "special",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "부대탈퇴지시",
    category: "special",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
  {
    actionCode: "인구이동",
    category: "special",
    safeToExecute: false,
    effectChecks: ["nation"],
  },
];

export const SAFE_GENERAL_EXECUTION_CODES = GENERAL_COMMANDS.filter(
  (command) => command.safeToExecute,
).map((command) => command.actionCode);

export const SAFE_NATION_EXECUTION_CODES = NATION_COMMANDS.filter(
  (command) => command.safeToExecute,
).map((command) => command.actionCode);

export const MAIN_SECTIONS = {
  serverInfo: ["현재:", "턴", "접속자"],
  cityInfo: ["인구", "농업", "상업", "치안", "수비", "성벽"],
  nationInfo: ["국가방침", "국력", "군주", "금", "쌀"],
  generalInfo: ["통솔", "무력", "지력", "정치", "매력"],
  news: ["장수 동향", "개인 기록", "중원 정세"],
  messages: ["전체", "국가", "개인", "외교"],
} as const;
