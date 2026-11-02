Node v22.23.3 (V8 12.4.254.21-node.57), apache-arrow 21.2.0
| Rows | Decode JSON | Decode Arrow | First screen JSON | First screen Arrow | Sum a column JSON | Sum a column Arrow | Memory JSON | Memory Arrow |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10,000 | 8.98 ms | 0.92 ms | 7.74 ms | 0.97 ms | 0.15 ms | 0.10 ms | 2.4 MB | 0.7 MB |
| 100,000 | 95.1 ms | 2.15 ms | 95.6 ms | 1.94 ms | 2.55 ms | 0.41 ms | 23.8 MB | 7.0 MB |
| 1,000,000 | 1213 ms | 7.23 ms | 1244 ms | 9.91 ms | 26.7 ms | 4.74 ms | 238.5 MB | 69.7 MB |
