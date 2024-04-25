import argparse
import csv


def manipulate_csv(input_file, output_file):
    with open(input_file, 'r') as file:
        reader = csv.DictReader(file)
        fieldnames = reader.fieldnames

        with open(output_file, 'w+', newline='') as output:
            writer = csv.DictWriter(output, fieldnames=fieldnames)
            writer.writeheader()

            for row in reader:
                if row['action'] == 'sbft':
                    reward = float(row['throughput']) * 2.5
                    row['throughput'] = str(reward)
                writer.writerow(row)

def main():
    parser = argparse.ArgumentParser(description='CSV Manipulation Tool')
    parser.add_argument('input_file', help='Path to the input CSV file')
    parser.add_argument('output_file', help='Path to the output CSV file')

    args = parser.parse_args()
    manipulate_csv(args.input_file, args.output_file)

if __name__ == '__main__':
    main()
