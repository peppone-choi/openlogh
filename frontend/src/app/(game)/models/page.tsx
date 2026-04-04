'use client';

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import { OBJLoader } from 'three/addons/loaders/OBJLoader.js';
import * as THREE from 'three';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { PageHeader } from '@/components/game/page-header';
import { Ship, Box, Globe, Castle, Search } from 'lucide-react';

/* Extend JSX for R3F intrinsic elements */
import type { ThreeElements } from '@react-three/fiber';
declare module 'react' {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace JSX {
        interface IntrinsicElements extends ThreeElements {}
    }
}

const CDN_BASE = 'https://raw.githubusercontent.com/peppone-choi/openlogh-image/main/3d-models';
const LOCAL_BASE = '/research-models';

type LodLevel = 'high' | 'medium' | 'low';

interface ModelFile {
    file: string;
    vertices: number;
    faces: number;
    size: number;
}

interface TextureInfo {
    file: string;
    size: number;
}

interface CatalogItem {
    id: string;
    faction: string;
    class: string;
    files: Record<string, ModelFile>;
    textures?: Record<string, TextureInfo>;
}

interface Catalog {
    ships: CatalogItem[];
    fortresses: CatalogItem[];
    planets: CatalogItem[];
    summary: {
        total_ships: number;
        total_fortresses: number;
        total_planets: number;
        total_vertices: number;
        total_faces: number;
        total_textures: number;
    };
}

function ModelViewer({
    modelUrl,
    textureUrl,
    bumpUrl,
    faction,
}: {
    modelUrl: string | null;
    textureUrl: string | null;
    bumpUrl: string | null;
    faction: string;
}) {
    const meshRef = useRef<THREE.Group>(null);
    const [obj, setObj] = useState<THREE.Group | null>(null);

    useEffect(() => {
        if (!modelUrl) {
            setObj(null);
            return;
        }

        const loader = new OBJLoader();
        loader.load(
            modelUrl,
            (loaded) => {
                const texLoader = new THREE.TextureLoader();
                const matOpts: THREE.MeshStandardMaterialParameters = {
                    color: faction === 'empire' ? 0x8899bb : faction === 'alliance' ? 0x99bb88 : 0xbbaa88,
                    metalness: 0.5,
                    roughness: 0.45,
                    side: THREE.DoubleSide,
                };

                let pending = 0;
                const applyMaterial = () => {
                    if (pending > 0) return;
                    const mat = new THREE.MeshStandardMaterial(matOpts);
                    loaded.traverse((child) => {
                        if ((child as THREE.Mesh).isMesh) {
                            (child as THREE.Mesh).material = mat;
                        }
                    });

                    const box = new THREE.Box3().setFromObject(loaded);
                    const center = box.getCenter(new THREE.Vector3());
                    const size = box.getSize(new THREE.Vector3());
                    loaded.position.sub(center);
                    const maxDim = Math.max(size.x, size.y, size.z);
                    if (maxDim > 0) loaded.scale.setScalar(8 / maxDim);

                    setObj(loaded);
                };

                if (textureUrl) {
                    pending++;
                    texLoader.load(
                        textureUrl,
                        (tex) => {
                            tex.wrapS = THREE.RepeatWrapping;
                            tex.wrapT = THREE.RepeatWrapping;
                            matOpts.map = tex;
                            matOpts.color = 0xffffff;
                            pending--;
                            applyMaterial();
                        },
                        undefined,
                        () => {
                            pending--;
                            applyMaterial();
                        }
                    );
                }
                if (bumpUrl) {
                    pending++;
                    texLoader.load(
                        bumpUrl,
                        (bump) => {
                            bump.wrapS = THREE.RepeatWrapping;
                            bump.wrapT = THREE.RepeatWrapping;
                            matOpts.bumpMap = bump;
                            matOpts.bumpScale = 0.3;
                            pending--;
                            applyMaterial();
                        },
                        undefined,
                        () => {
                            pending--;
                            applyMaterial();
                        }
                    );
                }
                if (!textureUrl && !bumpUrl) {
                    applyMaterial();
                }
            },
            undefined,
            (err) => console.error('Model load error:', err)
        );

        return () => setObj(null);
    }, [modelUrl, textureUrl, bumpUrl, faction]);

    useFrame(() => {
        if (meshRef.current) {
            meshRef.current.rotation.y += 0.002;
        }
    });

    if (!obj) return null;

    return (
        <group ref={meshRef}>
            <primitive object={obj} />
        </group>
    );
}

function Scene({
    modelUrl,
    textureUrl,
    bumpUrl,
    faction,
}: {
    modelUrl: string | null;
    textureUrl: string | null;
    bumpUrl: string | null;
    faction: string;
}) {
    return (
        <>
            <ambientLight intensity={0.4} color={0x404060} />
            <directionalLight position={[5, 10, 7]} intensity={1.0} color={0xaaccff} />
            <directionalLight position={[-5, -3, -5]} intensity={0.4} color={0xffaa66} />
            <gridHelper args={[60, 30, 0x1a1a3a, 0x1a1a3a]} />
            <ModelViewer modelUrl={modelUrl} textureUrl={textureUrl} bumpUrl={bumpUrl} faction={faction} />
            <OrbitControls enableDamping dampingFactor={0.05} />
        </>
    );
}

export default function ModelsPage() {
    const searchParams = useSearchParams();
    const source = searchParams.get('source') === 'research' ? 'research' : 'cdn';
    const [catalog, setCatalog] = useState<Catalog | null>(null);
    const [selected, setSelected] = useState<CatalogItem | null>(null);
    const [lod, setLod] = useState<LodLevel>('high');
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const base = source === 'research' ? LOCAL_BASE : CDN_BASE;
        fetch(`${base}/catalog.json`)
            .then((r) => r.json())
            .then((data) => {
                setCatalog(data);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, [source]);

    useEffect(() => {
        setSelected(null);
    }, [source]);

    const modelUrl = useMemo(() => {
        if (!selected) return null;
        const base = source === 'research' ? LOCAL_BASE : CDN_BASE;
        const lodFiles: Record<LodLevel, string[]> = {
            high: ['high.obj', 'model.obj'],
            medium: ['medium.obj', 'model.obj'],
            low: ['low.obj', 'model.obj'],
        };
        for (const fn of lodFiles[lod]) {
            const key = fn.replace('.obj', '');
            const file = selected.files[key];
            if (file) return `${base}/${selected.id}/${file.file ?? fn}`;
        }
        return null;
    }, [selected, lod, source]);

    const textureUrl = useMemo(() => {
        if (source === 'research') return null;
        if (!selected?.textures) return null;
        const key = lod === 'high' ? 'diffuse_high' : lod === 'medium' ? 'diffuse_medium' : 'diffuse_low';
        const tex = selected.textures[key] || selected.textures['diffuse'];
        return tex ? `${CDN_BASE}/${selected.id}/${tex.file}` : null;
    }, [selected, lod, source]);

    const bumpUrl = useMemo(() => {
        if (source === 'research') return null;
        if (!selected?.textures?.bump) return null;
        return `${CDN_BASE}/${selected.id}/${selected.textures.bump.file}`;
    }, [selected, source]);

    const filteredItems = useMemo(() => {
        if (!catalog) return { empire: [], alliance: [], fortresses: [], planets: [] };
        const q = search.toLowerCase();
        const filter = (items: CatalogItem[]) =>
            items.filter((i) => i.id.toLowerCase().includes(q) || i.class.toLowerCase().includes(q));
        return {
            empire: filter(catalog.ships.filter((s) => s.faction === 'empire')),
            alliance: filter(catalog.ships.filter((s) => s.faction === 'alliance')),
            fortresses: filter(catalog.fortresses),
            planets: filter(catalog.planets),
        };
    }, [catalog, search]);

    const fileInfo = useMemo(() => {
        if (!selected) return null;
        return selected.files[lod] || selected.files['model'] || null;
    }, [selected, lod]);

    const renderCategory = useCallback(
        (label: string, icon: React.ReactNode, items: CatalogItem[]) => {
            if (items.length === 0) return null;
            return (
                <div key={label}>
                    <div className="flex items-center gap-1.5 px-2 py-1.5 text-xs font-bold uppercase tracking-wider text-emerald-400">
                        {icon}
                        {label} ({items.length})
                    </div>
                    {items.map((item) => (
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
                                {(item.files.high || item.files.model)?.vertices?.toLocaleString() || '?'}v
                            </span>
                        </button>
                    ))}
                </div>
            );
        },
        [selected]
    );

    if (loading) {
        return (
            <div className="flex h-full items-center justify-center">
                <p className="text-zinc-500">Loading catalog...</p>
            </div>
        );
    }

    return (
        <div className="flex h-[calc(100vh-64px)] flex-col">
            <PageHeader title="3D Model Viewer" icon={Box} />
            <div className="flex flex-1 overflow-hidden">
                {/* Sidebar */}
                <div className="flex w-64 flex-col border-r border-zinc-800 bg-zinc-950/50">
                    <div className="border-b border-zinc-800 p-2">
                        <div className="mb-2 flex items-center gap-1">
                            <Badge variant={source === 'research' ? 'default' : 'outline'} className="text-[10px]">
                                {source === 'research' ? 'Research Local' : 'CDN'}
                            </Badge>
                            {catalog?.summary && (
                                <span className="text-[10px] text-zinc-500">
                                    {catalog.summary.total_ships} ships
                                </span>
                            )}
                        </div>
                        <div className="relative">
                            <Search className="absolute left-2 top-2 h-3.5 w-3.5 text-zinc-500" />
                            <Input
                                placeholder="Search..."
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                className="h-7 pl-7 text-xs"
                            />
                        </div>
                    </div>
                    <ScrollArea className="flex-1">
                        {renderCategory('Empire', <Ship className="h-3 w-3" />, filteredItems.empire)}
                        {renderCategory('Alliance', <Ship className="h-3 w-3" />, filteredItems.alliance)}
                        {renderCategory('Fortress', <Castle className="h-3 w-3" />, filteredItems.fortresses)}
                        {renderCategory('Planet', <Globe className="h-3 w-3" />, filteredItems.planets)}
                    </ScrollArea>
                </div>

                {/* 3D Viewport */}
                <div className="relative flex-1 bg-[#0a0a1a]">
                    <Canvas camera={{ position: [10, 7, 20], fov: 45 }}>
                        <Suspense fallback={null}>
                            <Scene
                                modelUrl={modelUrl}
                                textureUrl={textureUrl}
                                bumpUrl={bumpUrl}
                                faction={selected?.faction || 'unknown'}
                            />
                        </Suspense>
                    </Canvas>

                    {/* LOD Controls */}
                    <div className="absolute right-4 top-4 flex gap-1">
                        {(['high', 'medium', 'low'] as LodLevel[]).map((l) => (
                            <Button
                                key={l}
                                size="sm"
                                variant={lod === l ? 'default' : 'outline'}
                                onClick={() => setLod(l)}
                                className="h-7 text-xs"
                            >
                                {l.charAt(0).toUpperCase() + l.slice(1)}
                            </Button>
                        ))}
                    </div>

                    {/* Info Panel */}
                    {selected && (
                        <Card className="absolute bottom-4 right-4 w-52 bg-zinc-950/90 backdrop-blur">
                            <CardHeader className="p-3 pb-1">
                                <CardTitle className="text-sm text-blue-400">{selected.class}</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-0.5 p-3 pt-0 text-xs text-zinc-500">
                                <p>
                                    Faction:{' '}
                                    <Badge variant="outline" className="text-[10px]">
                                        {selected.faction}
                                    </Badge>
                                </p>
                                {fileInfo && (
                                    <>
                                        <p>
                                            Vertices:{' '}
                                            <span className="text-zinc-300">{fileInfo.vertices?.toLocaleString()}</span>
                                        </p>
                                        <p>
                                            Faces:{' '}
                                            <span className="text-zinc-300">{fileInfo.faces?.toLocaleString()}</span>
                                        </p>
                                    </>
                                )}
                                {selected.textures && (
                                    <p>
                                        Textures:{' '}
                                        <span className="text-zinc-300">{Object.keys(selected.textures).length}</span>
                                    </p>
                                )}
                                {source === 'research' && 'variants' in selected && (
                                    <p>
                                        Variants:{' '}
                                        <span className="text-zinc-300">
                                            {Object.keys((selected as CatalogItem & { variants?: Record<string, unknown> }).variants || {}).join(', ') || '-'}
                                        </span>
                                    </p>
                                )}
                            </CardContent>
                        </Card>
                    )}

                    {!selected && (
                        <div className="absolute inset-0 flex items-center justify-center">
                            <p className="text-sm text-zinc-600">Select a model from the sidebar</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
