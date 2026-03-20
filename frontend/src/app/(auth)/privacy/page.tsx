import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft } from 'lucide-react';

export default function PrivacyPage() {
    return (
        <Card className="w-full max-w-3xl p-8">
            <CardHeader className="px-0 pt-0">
                <div className="flex items-center gap-3">
                    <Button variant="ghost" size="icon" asChild>
                        <Link href="/login">
                            <ArrowLeft className="size-4" />
                        </Link>
                    </Button>
                    <CardTitle className="text-xl">개인정보 제공 및 이용에 대한 동의</CardTitle>
                </div>
            </CardHeader>
            <CardContent className="px-0 pb-0 space-y-6 text-sm leading-relaxed text-muted-foreground">
                <p>
                    아래 내용을 자세히 읽으신 후, 개인정보 제공 및 이용, 그리고 제3자 제공에 대한 동의 여부를 결정하여
                    주십시오.
                </p>

                <section className="space-y-3">
                    <h2 className="text-base font-semibold text-foreground">1. 개인정보 처리 내역</h2>
                    <p>오픈삼국 서비스 이용 시 개인정보 처리 내역은 다음과 같습니다.</p>
                    <ul className="list-disc pl-5 space-y-1">
                        <li>
                            <strong className="text-foreground">수집·이용 목적:</strong> 이용자 연령 확인, 중복 가입
                            여부, 개인별 참여 이력 관리 등 서비스 제공에 따른 활용
                        </li>
                        <li>
                            <strong className="text-foreground">수집 항목:</strong> 아이디, 이메일, 생년월일
                        </li>
                        <li>
                            <strong className="text-foreground">보유기간:</strong> 서비스 이용 기간 및 서비스 탈퇴 후
                            1개월
                        </li>
                    </ul>
                </section>

                <section className="space-y-3">
                    <h2 className="text-base font-semibold text-foreground">2. 개인정보 보호</h2>
                    <p>
                        원활한 서비스 제공을 위하여, 본 서비스는 위와 같은 개인정보가 필요하며, 「개인정보보호법」에
                        따라 이용자로부터 제공받는 개인정보를 보호합니다.
                    </p>
                </section>

                <section className="space-y-3">
                    <h2 className="text-base font-semibold text-foreground">3. 개인정보 관리 원칙</h2>
                    <p>
                        제공자는 처리 목적 범위 내에서만 관리·이용하며, 이용자는 언제든 열람 및 수정을 신청할 수
                        있습니다. 다만 법령상 보존 필요 시 수정 거부가 가능합니다.
                    </p>
                </section>

                <section className="space-y-3">
                    <h2 className="text-base font-semibold text-foreground">4. 개인정보 제3자 제공 내역</h2>
                    <p>없음</p>
                </section>

                <section className="space-y-3">
                    <h2 className="text-base font-semibold text-foreground">5. 동의 거부 권리</h2>
                    <p>
                        이용자는 개인정보 제공에 대한 동의를 거부할 권리가 있습니다. 그러나 동의를 거부할 경우, 원활한
                        서비스 제공에 제한을 받을 수 있습니다.
                    </p>
                </section>

                <section className="space-y-2 border-t border-border pt-4">
                    <h2 className="text-base font-semibold text-foreground">관리자 정보</h2>
                    <ul className="space-y-1">
                        <li>
                            <strong className="text-foreground">관리자:</strong> 빼뽀네(peppone-choi)
                        </li>
                    </ul>
                </section>
            </CardContent>
        </Card>
    );
}
