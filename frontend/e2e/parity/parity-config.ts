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

export interface PageMapItem {
  name: string;
  legacy: string;
  next: string;
}

export const PAGE_MAP: PageMapItem[] = [
  { name: "Main page", legacy: "/sam/che/", next: "/" },
  { name: "Map", legacy: "/sam/che/", next: "/map" },
  { name: "Commands", legacy: "/sam/che/", next: "/commands" },
  { name: "City info", legacy: "/sam/che/b_currentCity.php", next: "/city" },
  {
    name: "Nation info",
    legacy: "/sam/che/b_myKingdomInfo.php",
    next: "/nation",
  },
  { name: "General (my)", legacy: "/sam/che/", next: "/general" },
  { name: "Board", legacy: "/sam/che/v_board.php", next: "/board" },
  { name: "Troop", legacy: "/sam/che/v_troop.php", next: "/troop" },
  {
    name: "Diplomacy",
    legacy: "/sam/che/t_diplomacy.php",
    next: "/diplomacy",
  },
  {
    name: "Superior",
    legacy: "/sam/che/b_myBossInfo.php",
    next: "/superior",
  },
  {
    name: "Internal Affairs",
    legacy: "/sam/che/v_nationStratFinan.php",
    next: "/internal-affairs",
  },
  {
    name: "Chief Center",
    legacy: "/sam/che/v_chiefCenter.php",
    next: "/chief",
  },
  {
    name: "NPC Control",
    legacy: "/sam/che/v_NPCControl.php",
    next: "/npc-control",
  },
  {
    name: "General List",
    legacy: "/sam/che/b_genList.php",
    next: "/generals",
  },
  {
    name: "Tournament",
    legacy: "/sam/che/b_tournament.php",
    next: "/tournament",
  },
  {
    name: "Nation Cities",
    legacy: "/sam/che/b_myCityInfo.php",
    next: "/nation-cities",
  },
  {
    name: "Nation Generals",
    legacy: "/sam/che/v_nationGeneral.php",
    next: "/nation-generals",
  },
  {
    name: "Global Diplomacy",
    legacy: "/sam/che/v_globalDiplomacy.php",
    next: "/nations",
  },
  {
    name: "Current City",
    legacy: "/sam/che/b_currentCity.php",
    next: "/city",
  },
  {
    name: "Battle Center",
    legacy: "/sam/che/v_battleCenter.php",
    next: "/battle-center",
  },
  {
    name: "Inherit Point",
    legacy: "/sam/che/v_inheritPoint.php",
    next: "/inherit",
  },
  { name: "My Page", legacy: "/sam/che/b_myPage.php", next: "/my-page" },
  { name: "Auction", legacy: "/sam/che/v_auction.php", next: "/auction" },
  { name: "Betting", legacy: "/sam/che/b_betting.php", next: "/betting" },
  { name: "History", legacy: "/sam/che/v_history.php", next: "/history" },
  { name: "Vote", legacy: "/sam/che/v_vote.php", next: "/vote" },
  {
    name: "Best Generals",
    legacy: "/sam/che/a_bestGeneral.php",
    next: "/best-generals",
  },
  { name: "Emperor", legacy: "/sam/che/a_emperior.php", next: "/emperor" },
  {
    name: "Hall of Fame",
    legacy: "/sam/che/a_hallOfFame.php",
    next: "/hall-of-fame",
  },
  {
    name: "Kingdom List",
    legacy: "/sam/che/a_kingdomList.php",
    next: "/nations",
  },
  { name: "NPC List", legacy: "/sam/che/a_npcList.php", next: "/npc-list" },
  { name: "Traffic", legacy: "/sam/che/a_traffic.php", next: "/traffic" },
];

export const MAIN_SECTIONS = {
  serverInfo: ["현재:", "턴", "접속자"],
  cityInfo: ["인구", "충성", "농업", "상업", "치안", "수비", "성벽", "시세"],
  nationInfo: [
    "국가",
    "방침",
    "국력",
    "군주",
    "금",
    "쌀",
    "세율",
    "도시",
    "장수",
    "기술",
  ],
  generalInfo: [
    "통솔",
    "무력",
    "지력",
    "정치",
    "매력",
    "금",
    "쌀",
    "병사",
    "레벨",
    "나이",
  ],
  news: ["장수 동향", "개인 기록", "중원 정세"],
  messages: ["전체", "국가", "개인", "외교"],
} as const;
