import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { ArrowLeft } from 'lucide-react';

export default function TermsPage() {
    return (
        <Card className="w-full max-w-3xl p-8">
            <CardHeader className="px-0 pt-0">
                <div className="flex items-center gap-3">
                    <Button variant="ghost" size="icon" asChild>
                        <Link href="/login">
                            <ArrowLeft className="size-4" />
                        </Link>
                    </Button>
                    <CardTitle className="text-xl">오픈삼국 이용 약관</CardTitle>
                </div>
            </CardHeader>
            <CardContent className="px-0 pb-0 space-y-8 text-sm leading-relaxed text-muted-foreground">
                {/* 제1조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제1조 (목적)</h2>
                    <p>
                        이 약관은 오픈삼국 서비스 제공자가 제공하는 삼국지 모의전투 서비스와 관련하여 서비스 제공자와
                        이용자의 권리, 의무, 책임사항 및 기타 필요 사항을 규정함을 목적으로 합니다.
                    </p>
                </section>

                {/* 제2조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제2조 (정의)</h2>
                    <p>이 약관에서 사용하는 용어의 정의는 다음과 같습니다.</p>
                    <ol className="list-decimal pl-5 space-y-1">
                        <li>
                            &quot;서비스&quot;: 디스플레이가 구현되는 단말 장치(PC, 휴대폰 등)에서 제공되는 삼국지
                            모의전투 서비스 및 관련 제반 서비스
                        </li>
                        <li>
                            &quot;이용자&quot;: 서비스에 접속하여 본 약관에 따라 이용계약을 체결하고 서비스를 이용하는
                            자
                        </li>
                        <li>&quot;아이디&quot;: 이용자 식별과 서비스 이용을 위해 사용자가 정한 문자 조합</li>
                        <li>&quot;비밀번호&quot;: 아이디와 1:1로 매칭되어 이용자 신원 확인 및 보호를 위한 문자 조합</li>
                        <li>&quot;게시물&quot;: 이용자가 서비스상에 게시한 문자, 문서, 그림, 영상 등 시청각적 요소</li>
                        <li>
                            &quot;운영자&quot;: 서비스 제공자 및 서비스 제공자로부터 권한 위임받은 자로서 안정적이고
                            쾌적한 서비스 제공 의무를 가진 자
                        </li>
                    </ol>
                </section>

                {/* 제3조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제3조 (약관의 개정)</h2>
                    <ol className="list-none space-y-2">
                        <li>① 서비스 제공자는 현행법을 위배하지 않는 범위 내에서 본 약관을 개정할 수 있습니다.</li>
                        <li>
                            ② 약관 개정 시 적용일자 및 개정 사유를 명시하여 최소 30일 전부터 게시할 의무가 있으며,
                            이용자에게 현저하게 불리한 개정의 경우 전자우편 등으로 별도 통지해야 합니다.
                        </li>
                        <li>
                            ③ 이용자가 30일 기간 내에 서비스 탈퇴 또는 명시적 거부의사를 표시하지 않으면, 개정된 약관에
                            동의한 것으로 간주합니다.
                        </li>
                        <li>
                            ④ 이용자가 개정된 약관에 동의하지 않는 경우, 서비스 제공자는 기존 약관을 적용하기 현저하게
                            곤란한 특별한 사정이 있으면 이용계약을 해지할 수 있습니다.
                        </li>
                    </ol>
                </section>

                {/* 제4조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제4조 (약관의 게시)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서비스 제공자는 약관 내용을 이용자들이 쉽게 알 수 있도록 제공할 의무가 있으며, 이용자는
                            언제든지 열람할 권리를 가집니다.
                        </li>
                        <li>
                            ② 약관은 서비스 초기 화면을 통하여 접근할 수 있으며, 서비스 초기 화면에서 제공되는 약관
                            이외의 약관의 내용은 효력이 없습니다.
                        </li>
                    </ol>
                </section>

                {/* 제5조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제5조 (이용계약의 체결)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 이용계약은 가입희망자가 약관에 동의 후 회원가입 신청을 하고, 서비스 제공자가 승낙함으로써
                            체결됩니다.
                        </li>
                        <li>
                            ② 서비스 제공자는 원칙적으로 적법한 회원가입을 승낙하나, 다음에 해당하는 경우 거부하거나
                            사후 해지할 수 있습니다:
                            <ul className="list-disc pl-5 mt-1 space-y-1">
                                <li>실명이 아니거나 타인의 명의를 도용한 경우</li>
                                <li>14세 미만 아동이 법정대리인의 동의를 얻지 않은 경우</li>
                                <li>허위 정보 또는 기망 의도로 내용을 기재하지 않은 경우</li>
                                <li>본 약관 위반으로 이용 권한이 영구 박탈된 경우</li>
                                <li>운영자 과반 이상의 판단에 따라 원활한 서비스 제공에 문제가 될 사유가 있는 경우</li>
                            </ul>
                        </li>
                        <li>③ 이용계약은 서비스 제공자가 회원가입완료를 표시한 시점으로 성립합니다.</li>
                        <li>
                            ④ 서비스 제공자는 청소년보호법 등 관련 법령에 따라 등급 및 연령 준수를 위해 이용제한이나
                            등급별 제한을 할 수 있습니다.
                        </li>
                    </ol>
                </section>

                {/* 제6조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제6조 (회원정보)</h2>
                    <ol className="list-none space-y-2">
                        <li>① 회원정보는 서비스 이용자가 서비스 제공자에게 제공한 개인정보를 포함하는 정보입니다.</li>
                        <li>
                            ② 이용자는 개인정보 관리 화면을 통해 언제든지 본인 정보를 열람하고 수정할 수 있으나, 아이디,
                            생년월일 등은 수정이 제한될 수 있습니다.
                        </li>
                        <li>
                            ③ 이용자는 본인의 회원정보 관리 의무를 가지며, 변경 사항이 발생하면 지체 없이 통보해야
                            합니다.
                        </li>
                        <li>④ 제③항의 의무를 태만하게 하여 발생한 불이익에 대해 서비스 제공자는 책임지지 않습니다.</li>
                    </ol>
                </section>

                {/* 제7조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제7조 (개인정보보호의 의무)</h2>
                    <p>
                        서비스 제공자는 &quot;정보통신망법&quot; 등 관계 법령에 따라 이용자의 개인정보를 보호할 의무를
                        가집니다.
                    </p>
                </section>

                {/* 제8조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제8조 (아이디 및 비밀번호 관리 의무)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서버에 저장된 개인정보 이외의 개인정보(아이디 및 비밀번호 포함)에 대한 관리책임은
                            이용자에게 있습니다.
                        </li>
                        <li>
                            ② 서비스 제공자 및 운영자는 공공의 질서를 해칠 염려가 있거나 운영자로 오인할 염려가 큰
                            아이디의 이용을 제한할 수 있습니다.
                        </li>
                    </ol>
                </section>

                {/* 제9조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제9조 (서비스 제공자의 의무)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서비스 제공자는 금지된 행위를 하지 않으며, 계속적이고 안정적으로 서비스를 제공하기 위해
                            최선의 노력을 다할 의무를 가집니다.
                        </li>
                        <li>
                            ② 서비스 제공자는 이용자의 안전한 서비스 이용을 위해 개인정보를 적절하게 관리하고, 개인정보
                            제공 및 이용 방침을 공시하고 준수할 의무를 가집니다.
                        </li>
                        <li>
                            ③ 서비스 제공자는 이용자의 불만, 불편, 피해구제요청 및 이용자 간 분쟁을 적절하게 처리할
                            필요한 인력 및 시스템을 마련해야 합니다.
                        </li>
                        <li>
                            ④ 서비스 제공자는 이용자로부터 제기된 정당한 의견을 가능한 조속히 처리하고, 처리 과정 및
                            결과를 통보할 의무를 가집니다.
                        </li>
                    </ol>
                </section>

                {/* 제10조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제10조 (이용자의 의무)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 이용자는 원활한 서비스를 위해 다음의 행위를 해서는 안 됩니다:
                            <ul className="list-disc pl-5 mt-1 space-y-1">
                                <li>신청 또는 변경 시 허위 정보를 등록하는 행위</li>
                                <li>타인의 정보를 도용하는 행위</li>
                                <li>저작권 등 산업지식재산권 침해 행위</li>
                                <li>서비스 제공자의 업무를 방해하는 행위</li>
                                <li>선량한 공공의 질서를 어지럽히는 행위</li>
                                <li>기타 불법적이거나 부당한 행위</li>
                                <li>의도적으로 이용자 간 불화를 일으키는 행위</li>
                            </ul>
                        </li>
                        <li>
                            ② 이용자는 본 약관, 관련 법령, 서비스 제공자 및 운영자가 게시한 공지사항 및 통지사항을
                            준수할 의무를 가집니다.
                        </li>
                    </ol>
                </section>

                {/* 제11조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제11조 (서비스의 제공)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서비스 제공자는 다음의 서비스를 이용자에게 제공합니다:
                            <ul className="list-disc pl-5 mt-1 space-y-1">
                                <li>삼국지 모의전투</li>
                                <li>게시판 커뮤니티</li>
                                <li>삼국지 모의전투 내 채팅</li>
                                <li>기타 서비스 제공자가 추가 개발하거나 제휴 등을 통해 제공하는 서비스</li>
                            </ul>
                        </li>
                        <li>
                            ② 서비스 제공자는 서비스 이용가능시간을 별도로 지정할 수 있으며, 해당 시간은 사전에
                            공지되어야 합니다.
                        </li>
                        <li>③ 서비스는 연중무휴, 1일 24시간 제공함을 원칙으로 합니다.</li>
                        <li>
                            ④ 서비스 제공자는 기능개선, 유지보수, 점검, 고장, 통신두절, 천재지변 등 운영상 상당한 이유가
                            있으면 서비스 제공을 일시적으로 중단할 수 있습니다. 이 경우 사전에 공지할 의무가 있으나,
                            부득이한 사유가 있으면 그렇지 않습니다.
                        </li>
                    </ol>
                </section>

                {/* 제12조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제12조 (계약해제, 해지 등)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 이용자는 언제든지 회원탈퇴 기능을 통해 이용계약 해지신청을 할 수 있으며, 서비스 제공자는
                            관련 법령에 따라 지체 없이 처리해야 합니다.
                        </li>
                        <li>
                            ② 이용자가 이용계약을 해지할 경우, 개인정보처리방침에 따라 보유하는 경우를 제외하고는 지체
                            없이 모든 데이터를 삭제해야 합니다.
                        </li>
                        <li>
                            ③ 이용자가 이용계약을 해지하는 경우, 본인만 조회 가능한 게시물은 삭제되나, 타인에게 노출되는
                            게시물은 삭제되지 않을 수 있습니다.
                        </li>
                        <li>
                            ④ 이용자가 이용계약을 해제하는 경우, 서비스 제공자는 일정 기간 내 동일한 정보로 회원 가입을
                            제한할 수 있습니다.
                        </li>
                    </ol>
                </section>

                {/* 제13조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제13조 (이용제한)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서비스 제공자는 이용자가 본 약관의 의무를 위반하거나 서비스 정상 운영을 방해하는 경우,
                            경중 및 반복횟수에 따라 경고, 일시정지, 영구정지 등 단계적으로 이용을 제한할 수 있습니다.
                        </li>
                        <li>
                            ② 서비스 제공자는 아래의 위반이 발견되는 경우 즉시 영구이용정지를 할 수 있습니다:
                            <ul className="list-disc pl-5 mt-1 space-y-1">
                                <li>불법통신, 해킹, 악성프로그램 배포 등 보안에 중대한 위협을 주는 행위</li>
                                <li>명의도용 행위</li>
                                <li>불법프로그램의 제공/배포 및 운영방해 행위</li>
                                <li>기타 서비스 제공에 중대한 지장을 주는 행위</li>
                            </ul>
                        </li>
                    </ol>
                </section>

                {/* 제14조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제14조 (이용제한에 대한 이의신청)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 이용자는 제13조에 따른 이용제한이 부당하다고 판단되는 경우, 통보받은 날로부터 5일 이내에
                            불복사유를 기재한 이의신청서를 제출해야 합니다.
                        </li>
                        <li>
                            ② 이의신청서를 접수한 서비스 제공자는 지체 없이 접수 통보를 하며, 접수 날로부터 5일 이내에
                            답변해야 합니다.
                        </li>
                        <li>③ 서비스 제공자는 답변에 따라 상응하는 조치를 즉시 취해야 합니다.</li>
                    </ol>
                </section>

                {/* 제15조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제15조 (서비스 제공자의 책임제한)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서비스 제공자는 천재지변 또는 이에 준하는 불가항력으로 인해 서비스를 제공할 수 없는 경우
                            책임이 면제됩니다.
                        </li>
                        <li>② 서비스 제공자는 이용자의 귀책사유로 인한 서비스 이용 장애에 대해 책임지지 않습니다.</li>
                        <li>③ 서비스 제공자는 회원이 게재한 정보의 신뢰성과 정확성에 대해 책임지지 않습니다.</li>
                        <li>
                            ④ 서비스 제공자는 무료로 제공되는 서비스 이용과 관련하여 관련 법령에 특별한 규정이 없는 한
                            책임지지 않습니다.
                        </li>
                        <li>
                            ⑤ 서비스 제공자는 이용자의 서비스 이용 환경(네트워크, PC설정 등)으로 인한 문제에 대해
                            책임지지 않습니다.
                        </li>
                    </ol>
                </section>

                {/* 제16조 */}
                <section className="space-y-2">
                    <h2 className="text-base font-semibold text-foreground">제16조 (분쟁조정)</h2>
                    <ol className="list-none space-y-2">
                        <li>
                            ① 서비스 제공자는 서비스 내 이용자 간의 분쟁에는 개입하지 않고, 이용자 간의 합의를 수용하는
                            것을 원칙으로 합니다.
                        </li>
                        <li>
                            ② 다음의 경우에는 분쟁 조정을 위해 개입할 수 있습니다:
                            <ul className="list-disc pl-5 mt-1 space-y-1">
                                <li>분쟁 양 당사자 간의 합의 미성립 시 양 당사자 모두가 분쟁조정을 요청하는 경우</li>
                                <li>본 약관 또는 관련 법령을 분쟁 당사자 중 일방 또는 양방이 위배하는 경우</li>
                                <li>분쟁이 합리적인 시간 이상 지속되어 원활한 서비스를 방해하는 경우</li>
                            </ul>
                        </li>
                    </ol>
                </section>

                {/* 부칙 */}
                <section className="space-y-2 border-t border-border pt-4">
                    <h2 className="text-base font-semibold text-foreground">부칙</h2>
                    <p>본 약관은 2026년 3월 20일부터 적용됩니다.</p>
                </section>
            </CardContent>
        </Card>
    );
}
