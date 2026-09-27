package io.github.hanbernate.jsonbom.example;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import io.github.hanbernate.jsonbom.api.JsonBomMapper;
import io.github.hanbernate.jsonbom.api.Type;
import io.github.hanbernate.jsonbom.example.model.DeviceSnapshot;
import io.github.hanbernate.jsonbom.example.model.KwhSample;
import io.github.hanbernate.jsonbom.example.model.MeterReport;
import io.github.hanbernate.jsonbom.example.model.MeterSnapshot;
import io.github.hanbernate.jsonbom.example.model.RawPayload;
import io.github.hanbernate.jsonbom.example.model.Reading;
import io.github.hanbernate.jsonbom.example.repository.DeviceSnapshotRepository;
import io.github.hanbernate.jsonbom.example.repository.KwhRepository;
import io.github.hanbernate.jsonbom.example.repository.RawPayloadRepository;
import io.github.hanbernate.jsonbom.example.repository.ReadingRepository;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates the pieces needed to build a {@link MeterReport}.
 * <p>
 * It demonstrates several important points:
 * <ol>
 *     <li>The requested BOM is first <em>transformed</em> into the shape expected
 *         by the repositories (through the response type's paths). The
 *         transformed BOM is cached and re-used by every downstream call, while
 *         the <em>original</em> request BOM is kept for the final mapping —
 *         {@code JsonBomMapper.map} iterates the BOM by field name, so the
 *         transformed (path-rooted) BOM cannot be used there.</li>
 *     <li>Every {@code path[0]} of the response model must be present as a key in
 *         the {@code models} map, otherwise the field is silently left null.</li>
 *     <li>The transformed BOM mixes nested nodes ({@code device}) with leaf
 *         markers ({@code readings}, {@code kwh}, ...). The {@link #subBom}
 *         helper turns a nested node into its own BOM, keeps a leaf marker as a
 *         key-carrying BOM, and skips an absent key with an empty {@code Mono},
 *         so the repositories never see a {@code null} sub-BOM.</li>
 *     <li>Aggregate fields sharing one path root ({@code readings} /
 *         {@code readingSet}) may be backed by a single source — here a
 *         {@link Flux} collected into a {@code List} and a {@code Set}
 *         respectively.</li>
 * </ol>
 */
@Component
public class MeterReportOrchestrator {

    @Autowired
    DeviceSnapshotRepository deviceSnapshotRepository;
    @Autowired
    ReadingRepository readingRepository;
    @Autowired
    KwhRepository kwhRepository;
    @Autowired
    RawPayloadRepository rawPayloadRepository;
    @Autowired
    JsonBomMapper jsonBomMapper;

    /**
     * Resolves the report by calling each repository with the sub-BOM it asked
     * for.
     */
    @SuppressWarnings("unchecked")
    public Mono<MeterReport> getMeterReport(Mono<Bom> bom, Mono<String> deviceId) {
        Mono<Bom> upstreamBom = bom.map(this::upstreamBom).cache();

        Mono<DeviceSnapshot> device =
                deviceSnapshotRepository.findById(subBom(upstreamBom, "device"), deviceId);
        Flux<Reading> readings =
                readingRepository.findByDeviceId(subBom(upstreamBom, "readings"), deviceId);
        Mono<Reading[]> alerts =
                readingRepository.findAlerts(subBom(upstreamBom, "alerts"), deviceId);
        // The repository returns a raw sample; the registered KwhValueHandler
        // converts it during mapping.
        Mono<KwhSample> kwh =
                kwhRepository.findLatest(subBom(upstreamBom, "kwh"), deviceId);
        Mono<RawPayload> raw =
                rawPayloadRepository.load(subBom(upstreamBom, "raw"), deviceId);

        Map<String, Publisher<?>> models = new HashMap<>();
        models.put("device", device);
        models.put("readings", readings);
        // One reactive source can feed both the List and the Set field.
        models.put("readingSet", readings);
        models.put("alerts", alerts);
        models.put("kwh", kwh);
        models.put("raw", raw);

        // NOTE: the original request BOM (field names as keys) is mapped, not the
        // transformed one.
        return (Mono<MeterReport>) (Publisher<?>) jsonBomMapper.map(bom, MeterReport.class, models);
    }

    /**
     * Heterogeneous transformation variant.
     * <p>
     * The request BOM is keyed by the <em>target</em> field names ({@link
     * MeterReport}) exactly like {@link #getMeterReport(Mono, Mono)}. The mapper
     * first transforms it through {@link MeterReport}'s paths to build a {@link
     * MeterSnapshot} from the independent data sources, then expands that
     * snapshot into the final {@link MeterReport}.
     */
    @SuppressWarnings("unchecked")
    public Mono<MeterReport> getMeterReportViaSnapshot(Mono<Bom> bom, Mono<String> deviceId) {
        Map<String, Publisher<?>> sourceModels = new HashMap<>();
        sourceModels.put("device", deviceSnapshotRepository.findById(
                Mono.just(Bom.createWithEmptyValue("deviceId", "model")), deviceId));
        Flux<Reading> readings = readingRepository.findByDeviceId(Mono.just(new Bom()), deviceId);
        sourceModels.put("readings", readings);
        sourceModels.put("readingSet", readings);
        sourceModels.put("alerts", readingRepository.findAlerts(Mono.just(new Bom()), deviceId));
        sourceModels.put("kwh", kwhRepository.findLatest(Mono.just(new Bom()), deviceId));
        sourceModels.put("raw", rawPayloadRepository.load(Mono.just(new Bom()), deviceId));

        return (Mono<MeterReport>) (Publisher<?>) jsonBomMapper.map(
                bom, MeterReport.class, MeterSnapshot.class, sourceModels);
    }

    /**
     * Re-shapes the requested BOM according to {@link MeterReport}'s mapping, so
     * that repositories receive a sub-BOM keyed by their own field names.
     */
    private Bom upstreamBom(Bom targetBom) {
        return jsonBomMapper.getBomAdapter().transformBom(targetBom, MeterReport.class);
    }

    /**
     * Extracts the sub-BOM the downstream understands for {@code key} of the
     * transformed BOM:
     * <ul>
     *     <li>a nested node yields its own BOM;</li>
     *     <li>a leaf marker (such as {@code readings} or {@code kwh}) yields a
     *         BOM that still carries that key, so downstream
     *         {@code containsKey(...)} checks keep working;</li>
     *     <li>an absent key yields an empty {@code Mono}, so the downstream is
     *         skipped entirely.</li>
     * </ul>
     * Handing these sub-BOMs straight to the repositories avoids the nested
     * {@code flatMap} gating the orchestrator used to need.
     */
    private static Mono<Bom> subBom(Mono<Bom> upstreamBom, String key) {
        return upstreamBom.filter(b -> b.containsKey(key))
            .map(b -> {
                BomOrValue node = b.get(key);
                if (Type.BOM == node.getType() && null != node.bom()) {
                    return node.bom();
                }
                return Bom.createWithEmptyValue(key);
            });
    }
}
