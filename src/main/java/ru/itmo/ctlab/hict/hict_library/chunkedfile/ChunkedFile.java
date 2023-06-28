package ru.itmo.ctlab.hict.hict_library.chunkedfile;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IndexMap;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import ru.itmo.ctlab.hict.hict_library.trees.ContigTree;
import ru.itmo.ctlab.hict.hict_library.trees.ScaffoldTree;
import ru.itmo.ctlab.hict.hict_library.util.matrix.SparseCOOMatrixLong;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ChunkedFile {

  private final @NotNull @NonNull Path hdfFilePath;
  private final long blockCount;
  private final int denseBlockSize;
  private final long @NotNull @NonNull [] resolutions;
  private final Map<@NotNull @NonNull Long, @NotNull @NonNull Integer> resolutionToIndex;
  private final long @NotNull @NonNull [] matrixSizeBins;
  private final @NotNull @NonNull ContigTree contigTree;
  private final @NotNull @NonNull ScaffoldTree scaffoldTree;


  public ChunkedFile(final @NotNull @NonNull Path hdfFilePath, final int denseBlockSize) {
    this.hdfFilePath = hdfFilePath;
    // TODO: fix
    this.blockCount = 5;
    this.denseBlockSize = denseBlockSize;

    try (final var reader = HDF5Factory.openForReading(this.hdfFilePath.toFile())) {
      this.resolutions = reader.object().getAllGroupMembers("/resolutions").parallelStream().filter(s -> {
        try {
          Long.parseLong(s);
          return true;
        } catch (final NumberFormatException nfe) {
          return false;
        }
      }).mapToLong(Long::parseLong).toArray();

      this.resolutionToIndex = new ConcurrentHashMap<>();
      for (int i = 0; i < this.resolutions.length; i++) {
        this.resolutionToIndex.put(this.resolutions[i], i);
      }

      this.contigTree = new ContigTree();
      initializeContigTree();
      this.scaffoldTree = new ScaffoldTree();
      initializeScaffoldTree();
    }
  }

  private void initializeContigTree() {

  }

  private void initializeScaffoldTree() {
  }


  public long[][] getStripeIntersectionAsDenseMatrix(final long row, final long col, final long resolution) {
    try (final var reader = HDF5Factory.openForReading(this.hdfFilePath.toFile())) {
      final var blockIndexInDatasets = row * this.blockCount + col;
      final long blockLength;
      final long blockOffset;

      try (final var blockLengthDataset = reader.object().openDataSet(getBlockLengthDatasetPath(resolution))) {
        final long[] buf = reader.int64().readArrayBlockWithOffset(blockLengthDataset, 1, blockIndexInDatasets);
        blockLength = buf[0];
      }

      if (blockLength == 0L) {
        return new long[this.denseBlockSize][this.denseBlockSize];
      }

      try (final var blockOffsetDataset = reader.object().openDataSet(getBlockOffsetDatasetPath(resolution))) {
        final long[] buf = reader.int64().readArrayBlockWithOffset(blockOffsetDataset, 1, blockIndexInDatasets);
        blockOffset = buf[0];
      }


      final long[][] denseMatrix;

      if (blockOffset >= 0L) {
        // Fetch sparse block
        final long[] blockRows;
        final long[] blockCols;
        final long[] blockValues;

        try (final var blockRowsDataset = reader.object().openDataSet(getBlockRowsDatasetPath(resolution))) {
          blockRows = reader.int64().readArrayBlockWithOffset(blockRowsDataset, (int) blockLength, blockOffset);
        }
        try (final var blockColsDataset = reader.object().openDataSet(getBlockColsDatasetPath(resolution))) {
          blockCols = reader.int64().readArrayBlockWithOffset(blockColsDataset, (int) blockLength, blockOffset);
        }
        try (final var blockValuesDataset = reader.object().openDataSet(getBlockValuesDatasetPath(resolution))) {
          blockValues = reader.int64().readArrayBlockWithOffset(blockValuesDataset, (int) blockLength, blockOffset);
        }

        final var sparseMatrix = new SparseCOOMatrixLong(
          Arrays.stream(blockRows).mapToInt(l -> (int) l).toArray(),
          Arrays.stream(blockCols).mapToInt(l -> (int) l).toArray(),
          blockValues,
          false,
          (row == col)
        );
        denseMatrix = sparseMatrix.toDense(this.denseBlockSize, this.denseBlockSize);

      } else {
        // Fetch dense block
        final var idx = new IndexMap().bind(0, -(blockOffset + 1L)).bind(1, 0L);
        try (final var denseBlockDataset = reader.object().openDataSet(getDenseBlockDatasetPath(resolution))) {
          final var block = reader.int64().readSlicedMDArrayBlockWithOffset(denseBlockDataset, new int[]{this.denseBlockSize, this.denseBlockSize}, new long[]{0L, 0L}, idx);
          denseMatrix = block.toMatrix();
        }
      }

      return denseMatrix;
    }
  }


  private static @NotNull @NonNull String getBlockLengthDatasetPath(final long resolution) {
    return String.format("/resolutions/%d/treap_coo/block_length", resolution);
  }

  private static @NotNull @NonNull String getBlockOffsetDatasetPath(final long resolution) {
    return String.format("/resolutions/%d/treap_coo/block_offset", resolution);
  }

  private static @NotNull @NonNull String getBlockColsDatasetPath(final long resolution) {
    return String.format("/resolutions/%d/treap_coo/block_cols", resolution);
  }

  private static @NotNull @NonNull String getBlockRowsDatasetPath(final long resolution) {
    return String.format("/resolutions/%d/treap_coo/block_rows", resolution);
  }

  private static @NotNull @NonNull String getBlockValuesDatasetPath(final long resolution) {
    return String.format("/resolutions/%d/treap_coo/block_vals", resolution);
  }

  private static @NotNull @NonNull String getDenseBlockDatasetPath(final long resolution) {
    return String.format("/resolutions/%d/treap_coo/dense_blocks", resolution);
  }

  public @NotNull List<@NotNull Long> getResolutionsList() {
    return Arrays.stream(this.resolutions).boxed().toList();
  }

  public long @NotNull @NonNull [] getResolutions() {
    return this.resolutions;
  }
}
