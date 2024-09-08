with import <nixpkgs> {};
mkShell {
  nativeBuildInputs = [
    heroku
    (sbt.override { jre = openjdk11; })
  ];
}
