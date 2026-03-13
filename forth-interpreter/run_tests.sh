for i in $(seq 1 10); do
    echo "--- Test $i ---"
    cabal run FORTH -- tests/t$i.4TH > /tmp/actual.out 2>&1
    diff tests/t$i.out /tmp/actual.out && echo "PASS" || echo "FAIL"
done
