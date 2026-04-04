'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { OBJLoader } from 'three/addons/loaders/OBJLoader.js';
import { Search, Ship } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { PageHeader } from '@/components/game/page-header';

const LOCAL_BASE = '/research-models';

type LodLevel = 'high' | 'medium' | 'low' | 'n';

interface ModelFile {
    file: string;
    vertices: number;
    faces: number;
    size: number;
    source_file?: string;
    debug_files?: Record<string, ModelFile>;
}

interface CatalogItem {
    id: string;
    faction: string;
    class: string;
    files: Record<string, ModelFile>;
    variants?: Record<string, { source_file: string; desc_parts: number; heur_parts: number }>;
}

interface Catalog {
    ships: CatalogItem[];
    fortresses: CatalogItem[];
    planets: CatalogItem[];
    summary: {
        total_ships: number;
        total_vertices: number;
        total_faces: number;
        extracted_variants: number;
        failed_variants: number;
    };
}

function ObjViewport({
    modelUrls,
    faction,
    onStatusChange,
}: {
    modelUrls: Array<{ key: string; url: string; tint: number }>;
    faction: string;
    onStatusChange?: (status: { phase: 'idle' | 'loading' | 'loaded' | 'error'; detail?: string }) => void;
}) {
    const hostRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        const host = hostRef.current;
        if (!host) return;
        onStatusChange?.({
            phase: modelUrls.length > 0 ? 'loading' : 'idle',
            detail: modelUrls.length > 0 ? modelUrls.map((entry) => entry.key).join(', ') : 'no model url',
        });

        const scene = new THREE.Scene();
        scene.background = new THREE.Color(0x090b12);
        scene.fog = new THREE.Fog(0x090b12, 18, 52);

        const camera = new THREE.PerspectiveCamera(45, host.clientWidth / Math.max(host.clientHeight, 1), 0.1, 1000);
        camera.position.set(10, 7, 20);

        const renderer = new THREE.WebGLRenderer({ antialias: true });
        renderer.setPixelRatio(window.devicePixelRatio);
        renderer.setSize(host.clientWidth, host.clientHeight);
        host.appendChild(renderer.domElement);

        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        controls.dampingFactor = 0.05;

        scene.add(new THREE.AmbientLight(0x404060, 1.6));
        scene.add(new THREE.HemisphereLight(0xbfd7ff, 0x101010, 1.2));

        const key = new THREE.DirectionalLight(0xaaccff, 2.2);
        key.position.set(6, 12, 8);
        scene.add(key);

        const rim = new THREE.DirectionalLight(0xffaa66, 1.2);
        rim.position.set(-8, -4, -6);
        scene.add(rim);

        const grid = new THREE.GridHelper(60, 30, 0x1a1a3a, 0x1a1a3a);
        grid.position.y = -4;
        scene.add(grid);
        scene.add(new THREE.AxesHelper(3));

        let frame = 0;
        let disposed = false;
        const loadedGroups: THREE.Group[] = [];

        const animate = () => {
            if (disposed) return;
            frame = requestAnimationFrame(animate);
            for (const group of loadedGroups) {
                group.rotation.y += 0.002;
            }
            controls.update();
            renderer.render(scene, camera);
        };

        const handleResize = () => {
            if (!host) return;
            camera.aspect = host.clientWidth / Math.max(host.clientHeight, 1);
            camera.updateProjectionMatrix();
            renderer.setSize(host.clientWidth, host.clientHeight);
        };

        window.addEventListener('resize', handleResize);

        if (modelUrls.length > 0) {
            const loader = new OBJLoader();
            let pending = modelUrls.length;
            let maxRadius = 0;
            let lastDetail = '';

            const finishIfDone = () => {
                pending -= 1;
                if (pending > 0) return;
                if (maxRadius > 0) {
                    camera.near = 0.01;
                    camera.far = 2000;
                    camera.position.set(maxRadius * 1.8, maxRadius * 0.9, maxRadius * 1.8);
                    camera.updateProjectionMatrix();
                    controls.target.set(0, 0, 0);
                    controls.update();
                }
                onStatusChange?.({
                    phase: 'loaded',
                    detail: lastDetail || modelUrls.map((entry) => entry.key).join(', '),
                });
            };

            for (const entry of modelUrls) {
                loader.load(
                    entry.url,
                    (obj) => {
                    if (disposed) return;

                    const color =
                        entry.tint ||
                        (faction === 'empire' ? 0x93a7c9 : faction === 'alliance' ? 0x9abb8c : 0xc0b18f);
                    const material = new THREE.MeshStandardMaterial({
                        color,
                        metalness: 0.35,
                        roughness: 0.55,
                        side: THREE.DoubleSide,
                        transparent: true,
                        opacity: 0.98,
                    });

                    obj.traverse((child) => {
                        if ((child as THREE.Mesh).isMesh) {
                            (child as THREE.Mesh).material = material;
                        }
                    });

                    const box = new THREE.Box3().setFromObject(obj);
                    const center = box.getCenter(new THREE.Vector3());
                    const size = box.getSize(new THREE.Vector3());
                    obj.position.sub(center);
                    const maxDim = Math.max(size.x, size.y, size.z, 0.001);
                    obj.scale.setScalar(8 / maxDim);

                    const fittedBox = new THREE.Box3().setFromObject(obj);
                    const fittedSize = fittedBox.getSize(new THREE.Vector3());
                    const radius = Math.max(fittedSize.x, fittedSize.y, fittedSize.z, 0.001) * 0.9;
                    maxRadius = Math.max(maxRadius, radius);
                    lastDetail = `${entry.key}: ${size.x.toFixed(2)} x ${size.y.toFixed(2)} x ${size.z.toFixed(2)}`;
                    loadedGroups.push(obj);
                    scene.add(obj);
                    finishIfDone();
                    },
                    undefined,
                    (error) => {
                        console.error('OBJ load failed', error);
                        onStatusChange?.({
                            phase: 'error',
                            detail: `${entry.key}: ${error instanceof Error ? error.message : 'OBJ load failed'}`,
                        });
                    }
                );
            }
        }

        animate();

        return () => {
            disposed = true;
            cancelAnimationFrame(frame);
            window.removeEventListener('resize', handleResize);
            controls.dispose();
            renderer.dispose();
            for (const group of loadedGroups) {
                scene.remove(group);
            }
            if (renderer.domElement.parentElement === host) {
                host.removeChild(renderer.domElement);
            }
        };
    }, [modelUrls, faction, onStatusChange]);

    return <div ref={hostRef} className="h-full w-full" />;
}

export default function ResearchModelsClient() {
    const [catalog, setCatalog] = useState<Catalog | null>(null);
    const [selected, setSelected] = useState<CatalogItem | null>(null);
    const [lod, setLod] = useState<LodLevel>('high');
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [viewportStatus, setViewportStatus] = useState<{ phase: 'idle' | 'loading' | 'loaded' | 'error'; detail?: string }>({
        phase: 'idle',
    });
    const [showExact, setShowExact] = useState(false);
    const [showHeur, setShowHeur] = useState(false);
    const [showGap, setShowGap] = useState(false);
    const [showReplace, setShowReplace] = useState(false);

    useEffect(() => {
        fetch(`${LOCAL_BASE}/catalog.json`)
            .then((r) => r.json())
            .then((data: Catalog) => {
                setCatalog(data);
                setSelected(data.ships[0] ?? null);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, []);

    const filteredShips = useMemo(() => {
        if (!catalog) return [];
        const q = search.toLowerCase();
        return catalog.ships.filter((ship) => ship.id.toLowerCase().includes(q) || ship.class.toLowerCase().includes(q));
    }, [catalog, search]);

    const availableLods = useMemo(() => {
        if (!selected) return [] as LodLevel[];
        return (['high', 'medium', 'low', 'n'] as LodLevel[]).filter((entry) => selected.files[entry]);
    }, [selected]);

    useEffect(() => {
        if (!selected) return;
        if (!selected.files[lod]) {
            const nextLod = (['high', 'medium', 'low', 'n'] as LodLevel[]).find((entry) => selected.files[entry]);
            if (nextLod) setLod(nextLod);
        }
    }, [selected, lod]);

    const modelUrls = useMemo(() => {
        if (!selected) return [] as Array<{ key: string; url: string; tint: number }>;
        const file = selected.files[lod];
        if (!file) return [] as Array<{ key: string; url: string; tint: number }>;
        if (!showExact && !showHeur && !showGap && !showReplace) {
            return [{ key: 'full', url: `${LOCAL_BASE}/${selected.id}/${file.file}`, tint: 0 }];
        }
        const urls: Array<{ key: string; url: string; tint: number }> = [];
        const debug = file.debug_files ?? {};
        if (showExact && debug.exact?.file) {
            urls.push({ key: 'exact', url: `${LOCAL_BASE}/${selected.id}/${debug.exact.file}`, tint: 0x7dd3fc });
        }
        if (showHeur && debug.heur?.file) {
            urls.push({ key: 'heur', url: `${LOCAL_BASE}/${selected.id}/${debug.heur.file}`, tint: 0xf59e0b });
        }
        if (showGap && debug.gap?.file) {
            urls.push({ key: 'gap', url: `${LOCAL_BASE}/${selected.id}/${debug.gap.file}`, tint: 0xf43f5e });
        }
        if (showReplace && debug.replace?.file) {
            urls.push({ key: 'replace', url: `${LOCAL_BASE}/${selected.id}/${debug.replace.file}`, tint: 0x34d399 });
        }
        return urls;
    }, [selected, lod, showExact, showHeur, showGap, showReplace]);

    const fileInfo = selected?.files[lod] ?? null;
    const variantInfo = selected?.variants ?? {};

    const renderShip = useCallback(
        (item: CatalogItem) => (
            <button
                key={item.id}
                onClick={() => setSelected(item)}
                className={`block w-full px-3 py-1.5 text-left text-xs transition-colors ${
                    selected?.id === item.id
                        ? 'bg-blue-900/40 text-white'
                        : 'text-zinc-400 hover:bg-zinc-800/50 hover:text-zinc-200'
                }`}
            >
                <span>{item.class}</span>
                <span className="ml-1 text-[10px] text-zinc-600">
                    {Object.keys(item.files).join('/')}
                </span>
            </button>
        ),
        [selected]
    );

    if (loading) {
        return <div className="flex h-screen overflow-hidden items-center justify-center text-zinc-400">Loading research catalog...</div>;
    }

    return (
        <div className="flex h-screen overflow-hidden flex-col bg-[#05070d]">
            <PageHeader title="Research Ship Viewer" icon={Ship} description="Local re-extracted OBJ catalog" />
            <div className="flex min-h-0 flex-1 overflow-hidden">
                <div className="flex min-h-0 w-72 flex-col border-r border-zinc-800 bg-zinc-950/70">
                    <div className="border-b border-zinc-800 p-3">
                        <div className="mb-2 flex items-center gap-2">
                            <Badge variant="default" className="text-[10px]">
                                Research Local
                            </Badge>
                            {catalog?.summary && (
                                <span className="text-[10px] text-zinc-500">
                                    {catalog.summary.extracted_variants} extracted / {catalog.summary.failed_variants} failed
                                </span>
                            )}
                        </div>
                        <div className="relative">
                            <Search className="absolute left-2 top-2 h-3.5 w-3.5 text-zinc-500" />
                            <Input
                                placeholder="Search ship..."
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                className="h-8 pl-7 text-xs"
                            />
                        </div>
                    </div>
                    <ScrollArea className="min-h-0 flex-1">
                        <div className="px-2 py-2 text-[10px] font-bold uppercase tracking-wider text-emerald-400">
                            Ships ({filteredShips.length})
                        </div>
                        {filteredShips.map(renderShip)}
                    </ScrollArea>
                </div>

                <div className="relative flex-1">
                    <div className="absolute inset-0">
                        <ObjViewport
                            modelUrls={modelUrls}
                            faction={selected?.faction ?? 'unknown'}
                            onStatusChange={setViewportStatus}
                        />
                    </div>

                    <div className="absolute left-4 top-4 max-w-[28rem] rounded border border-zinc-700 bg-zinc-950/80 px-3 py-2 text-xs text-zinc-300 backdrop-blur">
                        <div className="font-mono text-[11px] text-zinc-500">viewer</div>
                        <div>Status: {viewportStatus.phase}</div>
                        <div>Mode: {!showExact && !showHeur && !showGap && !showReplace ? 'full' : [showExact && 'exact', showHeur && 'heur', showGap && 'gap', showReplace && 'replace'].filter(Boolean).join(', ')}</div>
                        <div>Model URL: {modelUrls.map((entry) => entry.key).join(', ') || '-'}</div>
                        <div>Detail: {viewportStatus.detail ?? '-'}</div>
                    </div>

                    <div className="absolute right-4 top-4 flex flex-wrap gap-1">
                        {availableLods.map((entry) => (
                            <button
                                key={entry}
                                type="button"
                                onClick={() => setLod(entry)}
                                className={`rounded border px-2 py-1 text-xs ${
                                    lod === entry
                                        ? 'border-blue-400 bg-blue-500/20 text-blue-100'
                                        : 'border-zinc-700 bg-zinc-950/70 text-zinc-400'
                                }`}
                            >
                                {entry}
                            </button>
                        ))}
                    </div>

                    <div className="absolute right-4 top-14 flex flex-wrap gap-1">
                        {[
                            ['exact', showExact, setShowExact],
                            ['heur', showHeur, setShowHeur],
                            ['gap', showGap, setShowGap],
                            ['replace', showReplace, setShowReplace],
                        ].map(([label, value, setter]) => (
                            <button
                                key={String(label)}
                                type="button"
                                onClick={() => (setter as (next: boolean) => void)(!(value as boolean))}
                                className={`rounded border px-2 py-1 text-xs ${
                                    value ? 'border-emerald-400 bg-emerald-500/20 text-emerald-100' : 'border-zinc-700 bg-zinc-950/70 text-zinc-400'
                                }`}
                            >
                                {String(label)}
                            </button>
                        ))}
                    </div>

                    {selected && (
                        <Card className="absolute bottom-4 right-4 w-72 bg-zinc-950/90 backdrop-blur">
                            <CardHeader>
                                <CardTitle className="text-blue-400">{selected.class}</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-1 text-xs text-zinc-400">
                                <p>
                                    Faction: <span className="text-zinc-200">{selected.faction}</span>
                                </p>
                                <p>
                                    Variant keys:{' '}
                                    <span className="text-zinc-200">{Object.keys(variantInfo).sort().join(', ') || '-'}</span>
                                </p>
                                {fileInfo && (
                                    <>
                                        <p>
                                            File: <span className="text-zinc-200">{fileInfo.source_file ?? fileInfo.file}</span>
                                        </p>
                                        <p>
                                            Vertices: <span className="text-zinc-200">{fileInfo.vertices.toLocaleString()}</span>
                                        </p>
                                        <p>
                                            Faces: <span className="text-zinc-200">{fileInfo.faces.toLocaleString()}</span>
                                        </p>
                                    </>
                                )}
                            </CardContent>
                        </Card>
                    )}
                </div>
            </div>
        </div>
    );
}
